package com.indra.minsait.dvsmart.indexing.adapter.out.batch.processor;

import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import com.indra.minsait.dvsmart.indexing.domain.service.FileMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        
        // Filtro 2: Skip directorios (no deberían llegar, pero por seguridad)
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
        
        // Extracción de metadata (lógica de dominio)
        try {
            ArchivoMetadata metadata = metadataService.toMetadata(entry);
            log.trace("Processed: {} → {}", entry.getFullPath(), metadata.getIdUnico());
            return metadata;
            
        } catch (Exception e) {
            log.error("Error processing file: {}", entry.getFullPath(), e);
            // Opción A: Retornar null (skip)
            // Opción B: Re-lanzar excepción (fail y retry)
            throw e;  // Fail-fast para errores inesperados
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
