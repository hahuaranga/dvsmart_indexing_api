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
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStream;
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
public class DirectoryQueueItemReader implements ItemReader<SftpFileEntry>, ItemStream {

    private final SftpRemoteFileTemplate sftpTemplate;
    private final DirectoryDiscoveryService discoveryService;
    private final String baseDir;
    
    private Queue<String> directoryQueue;
    private Queue<SftpFileEntry> currentDirectoryFiles;
    
    private int totalFilesRead = 0;
    private int directoriesProcessed = 0;
    private boolean discoveryCompleted = false;

    /**
     * ✅ CAMBIO: Constructor solo recibe baseDir
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

    // ✅ NUEVO: Implementar ItemStream para control de ciclo de vida
    @Override
    public void open(ExecutionContext executionContext) {
        log.info("========================================");
        log.info("🔄 OPEN: Initializing DirectoryQueueItemReader");
        log.info("Base directory: {}", baseDir);
        log.info("========================================");
        
        this.discoveryCompleted = false;
        this.directoryQueue = null;
        this.currentDirectoryFiles.clear();
        this.totalFilesRead = 0;
        this.directoriesProcessed = 0;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putInt("directoriesProcessed", directoriesProcessed);
        executionContext.putInt("totalFilesRead", totalFilesRead);
    }

    @Override
    public void close() {
        log.info("========================================");
        log.info("🛑 CLOSE: Cleaning up DirectoryQueueItemReader");
        log.info("Final stats: {} files, {} directories", totalFilesRead, directoriesProcessed);
        log.info("========================================");
        
        if (directoryQueue != null) {
            directoryQueue.clear();
        }
        currentDirectoryFiles.clear();
    }

    @Override
    public SftpFileEntry read() throws Exception {
        
        // ✅ LAZY DISCOVERY: Solo la primera vez
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
        
        // ✅ CRÍTICO: Cargar siguiente directorio
        String nextDirectory = directoryQueue.poll();  // ✅ poll() remueve de la queue
        loadDirectoryFiles(nextDirectory);
        directoriesProcessed++;
        
        // Log progreso
        if (directoriesProcessed % 100 == 0) {
            log.info("📊 Progress: {} directories processed, {} files indexed", 
                     directoriesProcessed, totalFilesRead);
        }
        
        return read(); // Recursión para retornar primer archivo
    }

    /**
     * ✅ Ejecuta discovery completo FRESH
     */
    private void executeDirectoryDiscovery() {
        log.info("========================================");
        log.info("PHASE 1: DIRECTORY DISCOVERY");
        log.info("Base directory: {}", baseDir);
        log.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        // ✅ Discovery SIEMPRE fresh
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
     * ✅ Carga archivos de UN directorio
     */
    private void loadDirectoryFiles(String directory) {
        try {
            sftpTemplate.execute(session -> {
                
                log.debug("📂 Scanning directory: {}", directory);
                
                SftpClient.DirEntry[] entries = session.list(directory);
                int filesInDir = 0;
                
                for (SftpClient.DirEntry entry : entries) {
                    String name = entry.getFilename();
                    
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    
                    // ✅ Solo procesar ARCHIVOS
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
            });
            
        } catch (Exception e) {
            log.error("❌ Error loading directory: {}", directory, e);
            throw new RuntimeException("Failed to load directory: " + directory, e);
        }
    }
}
