/*
 * /////////////////////////////////////////////////////////////////////////////
 *
 * Copyright (c) 2025 Indra Sistemas, S.A. All Rights Reserved.
 * http://www.indracompany.com/
 *
 * The contents of this file are owned by Indra Sistemas, S.A. copyright holder.
 * This file can only be copied, distributed and used all or in part with the
 * written permission of Indra Sistemas, S.A, or in accordance with the terms and
 * conditions laid down in the agreement / contract under which supplied.
 *
 * /////////////////////////////////////////////////////////////////////////////
 */
package com.indra.minsait.dvsmart.indexing.adapter.out.batch.reader;

import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 14:55:25
 * File: DirectoryQueueItemReader.java
 */

/**
 * Reader optimizado que usa SftpRemoteFileTemplate.
 * 
 * Ventajas vs Session directa:
 * - Thread-safe (puede usarse en async processors)
 * - Session pooling automático
 * - Error handling y retry integrados
 * - Código más limpio y mantenible
 */
@Slf4j
public class DirectoryQueueItemReader implements ItemReader<SftpFileEntry> {

    private final SftpRemoteFileTemplate sftpTemplate;
    private final Queue<String> directoryQueue;
    private Queue<SftpFileEntry> currentDirectoryFiles;
    
    private int totalFilesRead = 0;
    private int directoriesProcessed = 0;

    public DirectoryQueueItemReader(
            SftpRemoteFileTemplate sftpTemplate,
            Queue<String> directoryQueue) {
        this.sftpTemplate = sftpTemplate;
        this.directoryQueue = directoryQueue;
        this.currentDirectoryFiles = new LinkedList<>();
    }

    @Override
    public SftpFileEntry read() throws Exception {
        
        // Retornar archivos del directorio actual
        if (!currentDirectoryFiles.isEmpty()) {
            totalFilesRead++;
            return currentDirectoryFiles.poll();
        }
        
        // Si no hay más directorios, terminar
        if (directoryQueue.isEmpty()) {
            log.info("Indexing completed. Total files: {}, Directories: {}", 
                     totalFilesRead, directoriesProcessed);
            return null;
        }
        
        // Cargar siguiente directorio
        String nextDirectory = directoryQueue.poll();
        loadDirectoryFiles(nextDirectory);
        directoriesProcessed++;
        
        if (directoriesProcessed % 100 == 0) {
            log.info("Progress: {} directories, {} files", 
                     directoriesProcessed, totalFilesRead);
        }
        
        return read(); // Recursión para retornar primer archivo
    }

    /**
     * Carga archivos de un directorio usando template.
     * Session es adquirida y liberada automáticamente.
     */
    private void loadDirectoryFiles(String directory) {
        try {
            // ✅ Template maneja todo el ciclo de vida de la sesión
            sftpTemplate.execute(session -> {
                
                log.debug("Scanning directory: {}", directory);
                
                SftpClient.DirEntry[] entries = session.list(directory);
                int filesInDir = 0;
                
                for (SftpClient.DirEntry entry : entries) {
                    String name = entry.getFilename();
                    
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    
                    // Solo archivos (directorios ya están en queue)
                    if (!entry.getAttributes().isDirectory()) {
                        String fullPath = directory.endsWith("/") 
                            ? directory + name 
                            : directory + "/" + name;
                        
                        SftpFileEntry fileEntry = SftpFileEntry.builder()
                                .fullPath(fullPath)
                                .filename(name)
                                .size(entry.getAttributes().getSize())
                                .modificationTime(entry.getAttributes().getModifyTime().toMillis())
                                .isDirectory(false)
                                .build();
                        
                        currentDirectoryFiles.add(fileEntry);
                        filesInDir++;
                    }
                }
                
                log.debug("Loaded {} files from {}", filesInDir, directory);
                return null;
                
            }); // ← Session automáticamente liberada aquí
            
        } catch (Exception e) {
            log.error("Error loading directory: {}", directory, e);
            // Spring Batch manejará el retry según configuración
            throw new RuntimeException("Failed to load directory: " + directory, e);
        }
    }
}
