/*
 * /////////////////////////////////////////////////////////////////////////////
 *
 * Copyright (c) 2026 Indra Sistemas, S.A. All Rights Reserved.
 * http://www.indracompany.com/
 *
 * The contents of this file are owned by Indra Sistemas, S.A. copyright holder.
 * This file can only be copied, distributed and used all or in part with the
 * written permission of Indra Sistemas, S.A, or in accordance with the terms and
 * conditions laid down in the agreement / contract under which supplied.
 *
 * /////////////////////////////////////////////////////////////////////////////
 */
package com.indra.minsait.dvsmart.indexing.adapter.out.batch.processor;

import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import com.indra.minsait.dvsmart.indexing.domain.service.FileMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 16:33:10
 * File: MetadataExtractorProcessor.java
 */

/**
 * Processor que extrae metadata de archivos SFTP.
 * 
 * Responsabilidades:
 * - Filtrar directorios
 * - Filtrar archivos por extensión (opcional)
 * - Validar tamaños (opcional)
 * - Delegar extracción de metadata al servicio de dominio
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataExtractorProcessor implements ItemProcessor<SftpFileEntry, ArchivoMetadata> {

    private final FileMetadataService metadataService;
    
    // Configuración opcional de filtros
    private static final long MIN_FILE_SIZE = 0;           // 0 bytes = sin filtro
    private static final long MAX_FILE_SIZE = Long.MAX_VALUE; // Sin límite

    @Override
    public ArchivoMetadata process(SftpFileEntry entry) throws Exception {
        
        // Filtro 1: Skip nulls
        if (entry == null) {
            return null;
        }
        
        // Filtro 2: Skip directorios
        if (entry.isDirectory()) {
            log.trace("Skipping directory: {}", entry.getFullPath());
            return null;
        }
        
        // Filtro 3: Skip archivos ocultos
        if (entry.getFilename().startsWith(".")) {
            log.trace("Skipping hidden file: {}", entry.getFilename());
            return null;
        }
        
        // Filtro 4: Skip archivos temporales
        if (isTemporaryFile(entry.getFilename())) {
            log.trace("Skipping temporary file: {}", entry.getFilename());
            return null;
        }
        
        // Filtro 5: Validar tamaño
        if (entry.getSize() < MIN_FILE_SIZE || entry.getSize() > MAX_FILE_SIZE) {
            log.warn("Skipping file with invalid size: {} ({} bytes)", 
                     entry.getFullPath(), entry.getSize());
            return null;
        }
        
        // ✅ NUEVO: Capturar errores y retornar metadata con estado FAILED
        try {
            ArchivoMetadata metadata = metadataService.toMetadata(entry);
            log.trace("Processed: {} → {}", entry.getFullPath(), metadata.getIdUnico());
            return metadata;
            
        } catch (Exception e) {
            log.error("Error processing file: {}", entry.getFullPath(), e);
            
            // ✅ CAMBIO: En lugar de lanzar excepción, retornar metadata con error
            return createFailedMetadata(entry, e);
        }
    }

    /**
     * ✅ NUEVO: Crea metadata con estado FAILED cuando hay error
     */
    private ArchivoMetadata createFailedMetadata(SftpFileEntry entry, Exception error) {
        
        String idUnico;
        try {
            idUnico = metadataService.generateIdUnico(entry.getFullPath());
        } catch (Exception e) {
            // Fallback: usar hash simple del path
            idUnico = String.valueOf(entry.getFullPath().hashCode());
        }
        
        String errorMessage = String.format("%s: %s", 
            error.getClass().getSimpleName(), 
            error.getMessage() != null ? error.getMessage() : "Unknown error"
        );
        
        // Truncar mensaje si es muy largo
        if (errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 497) + "...";
        }
        
        return ArchivoMetadata.builder()
                .idUnico(idUnico)
                .sourcePath(entry.getFullPath())
                .fileName(entry.getFilename())
                .extension(extractExtension(entry.getFilename()))
                .fileSize(entry.getSize())
                .lastModificationDate(Instant.ofEpochMilli(entry.getModificationTime()))
                
                // ✅ Estado FAILED
                .indexing_status("FAILED")
                .indexing_indexedAt(Instant.now())
                .indexing_errorDescription(errorMessage)  // ✅ AQUÍ SE LLENA
                .build();
    }

    /**
     * ✅ NUEVO: Extrae extensión de forma segura
     */
    private String extractExtension(String filename) {
        try {
            if (filename == null || !filename.contains(".")) {
                return "";
            }
            int lastDot = filename.lastIndexOf('.');
            return filename.substring(lastDot + 1).toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Verifica si es un archivo temporal.
     */
    private boolean isTemporaryFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".tmp") 
            || lower.endsWith(".temp")
            || lower.endsWith(".bak")
            || lower.endsWith("~")
            || lower.startsWith("~$");  // MS Office temp files
    }
}
