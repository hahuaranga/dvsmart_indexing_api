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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 01:37:38
 * File: BatchMongoIndexesInitializer.java
 */

/**
 * Crea índices MongoDB requeridos para Spring Batch.
 * Seguro de ejecutar múltiples veces.
 */
@Component
public class BatchMongoIndexesInitializer {

    private final MongoTemplate mongoTemplate;

    public BatchMongoIndexesInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void createIndexes() {

        // BATCH_JOB_INSTANCE
        IndexOperations jobInstanceIdx =
                mongoTemplate.indexOps("BATCH_JOB_INSTANCE");

        jobInstanceIdx.createIndex(
            new Index()
                .on("jobName", Sort.Direction.ASC)
                .on("jobKey", Sort.Direction.ASC)
                .unique()
        );

        // BATCH_JOB_EXECUTION
        IndexOperations jobExecutionIdx =
                mongoTemplate.indexOps("BATCH_JOB_EXECUTION");

        jobExecutionIdx.createIndex(
            new Index()
                .on("jobInstanceId", Sort.Direction.ASC)
        );

        jobExecutionIdx.createIndex(
            new Index()
                .on("createTime", Sort.Direction.DESC)
        );

        // BATCH_STEP_EXECUTION
        IndexOperations stepExecutionIdx =
                mongoTemplate.indexOps("BATCH_STEP_EXECUTION");

        stepExecutionIdx.createIndex(
            new Index()
                .on("jobExecutionId", Sort.Direction.ASC)
        );

        stepExecutionIdx.createIndex(
            new Index()
                .on("stepName", Sort.Direction.ASC)
        );

        // BATCH_EXECUTION_CONTEXT
        IndexOperations executionContextIdx =
                mongoTemplate.indexOps("BATCH_EXECUTION_CONTEXT");

        executionContextIdx.createIndex(
            new Index()
                .on("executionId", Sort.Direction.ASC)
                .unique()
        );
    }
}
