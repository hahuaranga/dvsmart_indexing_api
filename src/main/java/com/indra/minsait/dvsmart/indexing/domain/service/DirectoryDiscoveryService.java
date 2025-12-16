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
package com.indra.minsait.dvsmart.indexing.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Service;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 14:40:25
 * File: DirectoryDiscoveryService.java
 */

/**
 * Servicio de descubrimiento de directorios usando SftpRemoteFileTemplate.
 */
@Slf4j
@Service
public class DirectoryDiscoveryService {

    /**
     * Descubre recursivamente todos los directorios bajo baseDir.
     * Usa template para manejo automático de sesiones.
     * 
     * @param sftpTemplate Template configurado
     * @param baseDir Directorio raíz
     * @return Cola thread-safe de directorios
     */
    public Queue<String> discoverDirectories(
            SftpRemoteFileTemplate sftpTemplate,
            String baseDir) {
        
        log.info("Starting directory discovery from: {}", baseDir);
        
        Queue<String> directories = new ConcurrentLinkedQueue<>();
        
        try {
            // ✅ Una sola sesión para todo el escaneo (más eficiente)
            sftpTemplate.execute(session -> {
                try {
					scanRecursive(session, baseDir, directories);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                return null;
            });
            
            log.info("Directory discovery completed. Total: {}", directories.size());
            return directories;
            
        } catch (Exception e) {
            log.error("Failed to discover directories", e);
            throw new RuntimeException("Directory discovery failed", e);
        }
    }

    /**
     * Escaneo recursivo BFS interno (dentro de una sesión).
     */
    private void scanRecursive(
            org.springframework.integration.file.remote.session.Session<SftpClient.DirEntry> session,
            String baseDir,
            Queue<String> directories) throws Exception {
        
        Queue<String> toExplore = new LinkedList<>();
        toExplore.add(baseDir);
        directories.add(baseDir);
        
        int dirCount = 0;
        
        while (!toExplore.isEmpty()) {
            String currentDir = toExplore.poll();
            
            SftpClient.DirEntry[] entries = session.list(currentDir);
            
            for (SftpClient.DirEntry entry : entries) {
                String name = entry.getFilename();
                
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                
                if (entry.getAttributes().isDirectory()) {
                    String fullPath = currentDir.endsWith("/") 
                        ? currentDir + name 
                        : currentDir + "/" + name;
                    
                    directories.add(fullPath);
                    toExplore.add(fullPath);
                    dirCount++;
                    
                    if (dirCount % 1000 == 0) {
                        log.info("Discovered {} directories...", dirCount);
                    }
                }
            }
        }
    }
}
