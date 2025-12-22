package com.indra.minsait.dvsmart.indexing.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 08:48:37
 * File: BatchMongoInitializer.java
 */

/**
 * Inicializador de MongoDB para Spring Batch.
 * ✅ FIX FINAL: Usa Update con $set para forzar tipo Long
 */
@Slf4j
@Component
public class BatchMongoInitializer {

    private static final String BATCH_JOB_INSTANCE = "BATCH_JOB_INSTANCE";
    private static final String BATCH_JOB_EXECUTION = "BATCH_JOB_EXECUTION";
    private static final String BATCH_STEP_EXECUTION = "BATCH_STEP_EXECUTION";
    private static final String BATCH_EXECUTION_CONTEXT = "BATCH_EXECUTION_CONTEXT";
    private static final String BATCH_SEQUENCES = "BATCH_SEQUENCES";

    private static final List<String> SEQUENCES = List.of(
        "BATCH_JOB_INSTANCE_SEQ",
        "BATCH_JOB_EXECUTION_SEQ",
        "BATCH_STEP_EXECUTION_SEQ"
    );

    private final MongoTemplate mongoTemplate;

    public BatchMongoInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initialize() {
        log.info("========================================");
        log.info("MongoDB Batch Initialization Started");
        log.info("========================================");
        
        createCollectionsIfMissing();
        ensureSequencesAsLong();  // ✅ MÉTODO NUEVO
        createIndexes();
        validateSequenceTypes();
        
        log.info("========================================");
        log.info("MongoDB Batch Initialization Completed");
        log.info("========================================");
    }

    /* -------------------------------------------------
     * 1️⃣ Colecciones
     * ------------------------------------------------- */
    private void createCollectionsIfMissing() {
        createIfMissing(BATCH_JOB_INSTANCE);
        createIfMissing(BATCH_JOB_EXECUTION);
        createIfMissing(BATCH_STEP_EXECUTION);
        createIfMissing(BATCH_EXECUTION_CONTEXT);
        createIfMissing(BATCH_SEQUENCES);
    }

    private void createIfMissing(String collection) {
        if (!mongoTemplate.collectionExists(collection)) {
            mongoTemplate.createCollection(collection);
            log.info("✅ Collection created: {}", collection);
        } else {
            log.debug("Collection exists: {}", collection);
        }
    }

    /* -------------------------------------------------
     * 2️⃣ ✅ SOLUCIÓN DEFINITIVA: Usar upsert con Update
     * ------------------------------------------------- */
    private void ensureSequencesAsLong() {
        log.info("Ensuring sequences are Long type...");
        
        for (String seq : SEQUENCES) {
            Query query = Query.query(Criteria.where("_id").is(seq));
            
            // ✅ CRÍTICO: Usar Update con setOnInsert para forzar Long
            Update update = new Update().setOnInsert("value", 0L);
            
            // Upsert garantiza:
            // 1. Si no existe, inserta con value=0L (Long)
            // 2. Si existe, NO lo modifica (mantiene valor actual)
            mongoTemplate.upsert(query, update, BATCH_SEQUENCES);
            
            log.info("✅ Sequence ensured: {} (using upsert)", seq);
        }
        
        // ✅ PASO ADICIONAL: Corregir tipos existentes incorrectos
        fixIntegerSequences();
    }

    /**
     * ✅ Corrige secuencias que fueron creadas como Integer
     */
    private void fixIntegerSequences() {
        for (String seq : SEQUENCES) {
            Query query = Query.query(Criteria.where("_id").is(seq));
            Document doc = mongoTemplate.findOne(query, Document.class, BATCH_SEQUENCES);
            
            if (doc != null) {
                Object value = doc.get("value");
                
                if (value instanceof Integer) {
                    log.warn("⚠️ Found Integer sequence: {} = {}, converting to Long", seq, value);
                    
                    Long longValue = ((Integer) value).longValue();
                    
                    // Actualizar con Update para forzar tipo
                    Update update = new Update().set("value", longValue);
                    mongoTemplate.updateFirst(query, update, BATCH_SEQUENCES);
                    
                    log.info("✅ Sequence converted: {} = {}L", seq, longValue);
                }
            }
        }
    }

    /* -------------------------------------------------
     * 3️⃣ Validación de Tipos
     * ------------------------------------------------- */
    private void validateSequenceTypes() {
        log.debug("Validating sequence types...");
        boolean allValid = true;
        
        for (String seq : SEQUENCES) {
            Query query = Query.query(Criteria.where("_id").is(seq));
            Document doc = mongoTemplate.findOne(query, Document.class, BATCH_SEQUENCES);
            
            if (doc != null) {
                Object value = doc.get("value");
                
                // ✅ CAMBIO: Aceptar tanto Long como Integer (el converter lo maneja)
                if (!(value instanceof Long || value instanceof Integer)) {
                    log.error("❌ INVALID TYPE: {} has type {}", seq, value.getClass());
                    allValid = false;
                } else if (value instanceof Integer) {
                    log.warn("⚠️ Sequence {} is Integer but will be converted to Long by custom converter", seq);
                } else {
                    log.trace("✓ Sequence {} is Long", seq);
                }
            } else {
                log.error("❌ MISSING SEQUENCE: {}", seq);
                allValid = false;
            }
        }
        
        if (!allValid) {
            throw new IllegalStateException(
                "❌ CRITICAL: Some sequences are missing or have invalid types"
            );
        }
        
        log.info("✅ Sequences validated (Converter handles Integer→Long)");
    }

    /* -------------------------------------------------
     * 4️⃣ Índices
     * ------------------------------------------------- */
    private void createIndexes() {
        log.debug("Creating indexes...");

        // BATCH_JOB_INSTANCE
        IndexOperations jobInstanceIdx = mongoTemplate.indexOps(BATCH_JOB_INSTANCE);
        jobInstanceIdx.createIndex(
            new Index()
                .on("jobName", Sort.Direction.ASC)
                .on("jobKey", Sort.Direction.ASC)
                .unique()
        );

        // BATCH_JOB_EXECUTION
        IndexOperations jobExecutionIdx = mongoTemplate.indexOps(BATCH_JOB_EXECUTION);
        jobExecutionIdx.createIndex(new Index().on("jobInstanceId", Sort.Direction.ASC));
        jobExecutionIdx.createIndex(new Index().on("createTime", Sort.Direction.DESC));

        // BATCH_STEP_EXECUTION
        IndexOperations stepExecutionIdx = mongoTemplate.indexOps(BATCH_STEP_EXECUTION);
        stepExecutionIdx.createIndex(new Index().on("jobExecutionId", Sort.Direction.ASC));
        stepExecutionIdx.createIndex(new Index().on("stepName", Sort.Direction.ASC));

        // BATCH_EXECUTION_CONTEXT
        IndexOperations executionContextIdx = mongoTemplate.indexOps(BATCH_EXECUTION_CONTEXT);
        executionContextIdx.createIndex(
            new Index().on("executionId", Sort.Direction.ASC).unique()
        );
        
        log.info("✅ Indexes ensured");
    }
}