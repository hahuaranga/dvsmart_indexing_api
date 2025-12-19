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
import com.indra.minsait.dvsmart.indexing.domain.service.DirectoryDiscoveryService;
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
 * Reader optimizado con LAZY DISCOVERY.
 * 
 * ESTRATEGIA HYBRID STREAMING:
 * - Primera llamada a read(): Ejecuta discovery completo de directorios
 * - Siguientes llamadas: Procesa archivos directorio por directorio
 * - Memoria: O(D) donde D = archivos en el directorio actual
 * 
 * Ventajas:
 * - Discovery se ejecuta solo cuando se lanza el job (no al arrancar la app)
 * - Thread-safe con SftpRemoteFileTemplate
 * - Sesiones SFTP del pool lazy se usan eficientemente
 */
@Slf4j
public class DirectoryQueueItemReader implements ItemReader<SftpFileEntry> {

    private final SftpRemoteFileTemplate sftpTemplate;
    private final DirectoryDiscoveryService discoveryService;
    private final String baseDir;
    
    private Queue<String> directoryQueue;
    private Queue<SftpFileEntry> currentDirectoryFiles;
    
    private int totalFilesRead = 0;
    private int directoriesProcessed = 0;
    private boolean discoveryCompleted = false;

    /**
     * Constructor con discovery lazy.
     */
    public DirectoryQueueItemReader(
            SftpRemoteFileTemplate sftpTemplate,
            DirectoryDiscoveryService discoveryService,
            String baseDir) {
        this.sftpTemplate = sftpTemplate;
        this.discoveryService = discoveryService;
        this.baseDir = baseDir;
        this.currentDirectoryFiles = new LinkedList<>();
    }

	@Override
    public SftpFileEntry read() throws Exception {
        
        // ✅ LAZY DISCOVERY: Solo ejecutar la primera vez que se llama read()
        if (!discoveryCompleted) {
            executeDirectoryDiscovery();
            discoveryCompleted = true;
        }
        
        // Retornar archivos del directorio actual
        if (!currentDirectoryFiles.isEmpty()) {
            totalFilesRead++;
            return currentDirectoryFiles.poll();
        }
        
        // Si no hay más directorios, terminar
        if (directoryQueue == null || directoryQueue.isEmpty()) {
            log.info("========================================");
            log.info("✅ INDEXING COMPLETED");
            log.info("Total files indexed: {}", totalFilesRead);
            log.info("Total directories processed: {}", directoriesProcessed);
            log.info("========================================");
            return null;
        }
        
        // Cargar siguiente directorio
        String nextDirectory = directoryQueue.poll();
        loadDirectoryFiles(nextDirectory);
        directoriesProcessed++;
        
        // Log progreso cada 100 directorios
        if (directoriesProcessed % 100 == 0) {
            log.info("📊 Progress: {} directories processed, {} files indexed", 
                     directoriesProcessed, totalFilesRead);
        }
        
        return read(); // Recursión para retornar primer archivo
    }

    /**
     * Ejecuta el discovery completo de directorios.
     * Solo se llama una vez, la primera vez que se invoca read().
     */
    private void executeDirectoryDiscovery() {
        log.info("========================================");
        log.info("PHASE 1: DIRECTORY DISCOVERY");
        log.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        // ✅ Discovery usa template (sesión automática del pool lazy)
        directoryQueue = discoveryService.discoverDirectories(sftpTemplate, baseDir);
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("========================================");
        log.info("✅ Discovery completed in {} ms ({} seconds)", duration, duration / 1000);
        log.info("Total directories to process: {}", directoryQueue.size());
        log.info("========================================");
        log.info("PHASE 2: FILE INDEXING");
        log.info("========================================");
    }

    /**
     * Carga archivos de un directorio usando template.
     * Session es adquirida y liberada automáticamente por el pool lazy.
     */
    private void loadDirectoryFiles(String directory) {
        try {
            // ✅ Template maneja todo el ciclo de vida de la sesión
            sftpTemplate.execute(session -> {
                
                log.debug("📂 Scanning directory: {}", directory);
                
                SftpClient.DirEntry[] entries = session.list(directory);
                int filesInDir = 0;
                
                for (SftpClient.DirEntry entry : entries) {
                    String name = entry.getFilename();
                    
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    
                    // ✅ Solo procesar ARCHIVOS (directorios ya están en la queue)
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
                
                if (filesInDir > 0) {
                    log.debug("📄 Loaded {} files from {}", filesInDir, directory);
                } else {
                    log.trace("📭 Empty directory: {}", directory);
                }
                
                return null;
                
            }); // ← Session automáticamente liberada aquí
            
        } catch (Exception e) {
            log.error("❌ Error loading directory: {}", directory, e);
            // Spring Batch manejará el retry según configuración
            throw new RuntimeException("Failed to load directory: " + directory, e);
        }
    }
}
