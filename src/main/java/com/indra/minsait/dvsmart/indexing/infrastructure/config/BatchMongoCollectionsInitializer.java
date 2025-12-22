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
package com.indra.minsait.dvsmart.indexing.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 01:33:30
 * File: BatchMongoCollectionsInitializer.java
 */

/**
 * Inicializa las colecciones requeridas por Spring Batch
 * cuando se usa MongoDB como JobRepository.
 *
 * Seguro de ejecutar múltiples veces (idempotente).
 */
@Component
public class BatchMongoCollectionsInitializer {

    private static final List<String> COLLECTIONS = List.of(
        "BATCH_JOB_INSTANCE",
        "BATCH_JOB_EXECUTION",
        "BATCH_STEP_EXECUTION",
        "BATCH_EXECUTION_CONTEXT",
        "BATCH_SEQUENCES"
    );

    private static final List<String> SEQUENCES = List.of(
        "BATCH_JOB_INSTANCE_SEQ",
        "BATCH_JOB_EXECUTION_SEQ",
        "BATCH_STEP_EXECUTION_SEQ"
    );

    private final MongoTemplate mongoTemplate;

    public BatchMongoCollectionsInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initialize() {
        createCollectionsIfMissing();
        initializeSequences();
    }

    private void createCollectionsIfMissing() {
        for (String collection : COLLECTIONS) {
            if (!mongoTemplate.collectionExists(collection)) {
                mongoTemplate.createCollection(collection);
            }
        }
    }

    private void initializeSequences() {
        for (String seq : SEQUENCES) {
            Query query = Query.query(Criteria.where("_id").is(seq));
            if (!mongoTemplate.exists(query, "BATCH_SEQUENCES")) {
                mongoTemplate.insert(
                    new Document("_id", seq).append("value", 0L),
                    "BATCH_SEQUENCES"
                );
            }
        }
    }
}
