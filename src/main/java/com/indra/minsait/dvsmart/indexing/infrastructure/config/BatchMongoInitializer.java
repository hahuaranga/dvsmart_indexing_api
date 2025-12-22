package com.indra.minsait.dvsmart.indexing.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 08:48:37
 * File: BatchMongoInitializer.java
 */

/**
 * Inicializador único de MongoDB para Spring Batch.
 *
 * - Crea colecciones
 * - Inicializa secuencias (Long)
 * - Crea índices
 *
 * Idempotente y seguro para múltiples arranques.
 */
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
        createCollectionsIfMissing();
        initializeSequences();
        createIndexes();
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
        }
    }

    /* -------------------------------------------------
     * 2️⃣ Secuencias (Long ONLY)
     * ------------------------------------------------- */
    private void initializeSequences() {
        for (String seq : SEQUENCES) {
            Query query = Query.query(Criteria.where("_id").is(seq));
            Document existing =
                mongoTemplate.findOne(query, Document.class, BATCH_SEQUENCES);

            if (existing == null) {
                mongoTemplate.insert(
                    new Document("_id", seq)
                        .append("value", Long.valueOf(0)),
                    BATCH_SEQUENCES
                );
            }
        }
    }

    /* -------------------------------------------------
     * 3️⃣ Índices
     * ------------------------------------------------- */
    private void createIndexes() {

        // BATCH_JOB_INSTANCE
        IndexOperations jobInstanceIdx =
            mongoTemplate.indexOps(BATCH_JOB_INSTANCE);

        jobInstanceIdx.createIndex(
            new Index()
                .on("jobName", Sort.Direction.ASC)
                .on("jobKey", Sort.Direction.ASC)
                .unique()
        );

        // BATCH_JOB_EXECUTION
        IndexOperations jobExecutionIdx =
            mongoTemplate.indexOps(BATCH_JOB_EXECUTION);

        jobExecutionIdx.createIndex(
            new Index().on("jobInstanceId", Sort.Direction.ASC)
        );

        jobExecutionIdx.createIndex(
            new Index().on("createTime", Sort.Direction.DESC)
        );

        // BATCH_STEP_EXECUTION
        IndexOperations stepExecutionIdx =
            mongoTemplate.indexOps(BATCH_STEP_EXECUTION);

        stepExecutionIdx.createIndex(
            new Index().on("jobExecutionId", Sort.Direction.ASC)
        );

        stepExecutionIdx.createIndex(
            new Index().on("stepName", Sort.Direction.ASC)
        );

        // BATCH_EXECUTION_CONTEXT
        IndexOperations executionContextIdx =
            mongoTemplate.indexOps(BATCH_EXECUTION_CONTEXT);

        executionContextIdx.createIndex(
            new Index()
                .on("executionId", Sort.Direction.ASC)
                .unique()
        );
    }
}