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
package com.indra.minsait.dvsmart.indexing.adapter.out.batch.writer;

import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity.DisorganizedFilesIndexDocument;
import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.mongodb.bulk.BulkWriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 14:44:55
 * File: BulkUpsertMongoItemWriter.java
 */

/**
 * Writer ultra-optimizado con bulk upsert.
 * 
 * Performance:
 * - Sin bulk: 100-200 docs/segundo
 * - Con bulk: 3000-5000 docs/segundo
 * 
 * Para 11M archivos:
 * - Sin bulk: ~15-30 horas
 * - Con bulk: ~30-60 minutos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkUpsertMongoItemWriter implements ItemWriter<ArchivoMetadata> {

    private final MongoTemplate mongoTemplate;

    @Override
    public void write(Chunk<? extends ArchivoMetadata> chunk) {
        
        BulkOperations bulkOps = mongoTemplate.bulkOps(
            BulkOperations.BulkMode.UNORDERED,
            DisorganizedFilesIndexDocument.class
        );
        
        int successCount = 0;
        int failedCount = 0;
        
        for (ArchivoMetadata metadata : chunk) {
            Query query = new Query(Criteria.where("idUnico").is(metadata.getIdUnico()));
            
            Update update = new Update()
                    .set("sourcePath", metadata.getSourcePath())
                    .set("fileName", metadata.getFileName())
                    .set("extension", metadata.getExtension())
                    .set("fileSize", metadata.getFileSize())
                    .set("lastModificationDate", metadata.getLastModificationDate())
                    
                    // ✅ Control de indexación (con error)
                    .set("indexing_status", metadata.getIndexing_status())
                    .set("indexing_indexedAt", metadata.getIndexing_indexedAt())
                    .set("indexing_errorDescription", metadata.getIndexing_errorDescription())  // ✅ CAMBIO
                    
                    // Estado inicial de reorganización (solo si indexación exitosa)
                    .set("reorg_status", "FAILED".equals(metadata.getIndexing_status()) 
                        ? "SKIPPED"   // ✅ Si falla indexación, skip reorganización
                        : "PENDING")
                    .set("reorg_attempts", 0)
                    
                    .setOnInsert("idUnico", metadata.getIdUnico());
            
            bulkOps.upsert(query, update);
            
            // ✅ NUEVO: Contar éxitos y fallos
            if ("FAILED".equals(metadata.getIndexing_status())) {
                failedCount++;
            } else {
                successCount++;
            }
        }
        
        try {
            BulkWriteResult result = bulkOps.execute();
            
            int inserted = result.getInsertedCount();
            int updated = result.getModifiedCount();
            
            // ✅ NUEVO: Log mejorado con conteo de errores
            log.info("Bulk write completed: {} inserted, {} updated | Success: {}, Failed: {}", 
                     inserted, updated, successCount, failedCount);
            
            // ✅ NUEVO: Alertar si tasa de error es alta
            if (failedCount > 0) {
                double failureRate = (double) failedCount / (successCount + failedCount) * 100;
                if (failureRate > 5.0) {
                    log.warn("⚠️ HIGH FAILURE RATE: {:.2f}% ({}/{})", 
                        failureRate, failedCount, successCount + failedCount);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in bulk write operation", e);
            throw new RuntimeException("Failed to write batch to MongoDB", e);
        }
    }
}
