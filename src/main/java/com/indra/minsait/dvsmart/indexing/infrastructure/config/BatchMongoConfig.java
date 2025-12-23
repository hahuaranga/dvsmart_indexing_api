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

import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.dao.mongodb.MongoExecutionContextDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoJobExecutionDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoJobInstanceDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoStepExecutionDao;
import org.springframework.batch.core.repository.support.SimpleJobRepository;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 23-12-2025 at 15:47:44
 * File: BatchMongoConfig.java
 */

/**
 * Configuración MANUAL de Spring Batch (SIN @EnableBatchProcessing)
 * para tener control total sobre los DAOs
 */
@Configuration
public class BatchMongoConfig {

    private static final String SEQUENCES_COLLECTION = "BATCH_SEQUENCES";
    private static final String JOB_INSTANCE_SEQ = "BATCH_JOB_INSTANCE_SEQ";
    private static final String JOB_EXECUTION_SEQ = "BATCH_JOB_EXECUTION_SEQ";
    private static final String STEP_EXECUTION_SEQ = "BATCH_STEP_EXECUTION_SEQ";

    @Bean
    @Primary
    MongoTemplate batchMongoTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter converter) {

        MappingMongoConverter mappingConverter = (MappingMongoConverter) converter;
        mappingConverter.setMapKeyDotReplacement("_");
        mappingConverter.afterPropertiesSet();
        return new MongoTemplate(databaseFactory, mappingConverter);
    }

    @Bean
    @Primary
    PlatformTransactionManager batchTransactionManager() {
        return new ResourcelessTransactionManager();
    }

    @Bean
    FixedMongoSequenceIncrementer jobInstanceIncrementer(MongoTemplate batchMongoTemplate) {
        return new FixedMongoSequenceIncrementer(
            batchMongoTemplate, SEQUENCES_COLLECTION, JOB_INSTANCE_SEQ
        );
    }

    @Bean
    FixedMongoSequenceIncrementer jobExecutionIncrementer(MongoTemplate batchMongoTemplate) {
        return new FixedMongoSequenceIncrementer(
            batchMongoTemplate, SEQUENCES_COLLECTION, JOB_EXECUTION_SEQ
        );
    }

    @Bean
    FixedMongoSequenceIncrementer stepExecutionIncrementer(MongoTemplate batchMongoTemplate) {
        return new FixedMongoSequenceIncrementer(
            batchMongoTemplate, SEQUENCES_COLLECTION, STEP_EXECUTION_SEQ
        );
    }

    @Bean
    @Primary
    MongoJobInstanceDao jobInstanceDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer jobInstanceIncrementer) {
        
        MongoJobInstanceDao dao = new MongoJobInstanceDao(batchMongoTemplate);
        dao.setJobInstanceIncrementer(jobInstanceIncrementer);
        return dao;
    }

    @Bean
    @Primary
    MongoJobExecutionDao jobExecutionDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer jobExecutionIncrementer) {
        
        MongoJobExecutionDao dao = new MongoJobExecutionDao(batchMongoTemplate);
        dao.setJobExecutionIncrementer(jobExecutionIncrementer);
        return dao;
    }

    @Bean
    @Primary
    MongoStepExecutionDao stepExecutionDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer stepExecutionIncrementer) {
        
        MongoStepExecutionDao dao = new MongoStepExecutionDao(batchMongoTemplate);
        dao.setStepExecutionIncrementer(stepExecutionIncrementer);
        return dao;
    }

    @Bean
    @Primary
    MongoExecutionContextDao executionContextDao(MongoTemplate batchMongoTemplate) {
        return new MongoExecutionContextDao(batchMongoTemplate);
    }

    @Bean
    @Primary
    JobRepository jobRepository(
            MongoJobInstanceDao jobInstanceDao,
            MongoJobExecutionDao jobExecutionDao,
            MongoStepExecutionDao stepExecutionDao,
            MongoExecutionContextDao executionContextDao) {

        return new SimpleJobRepository(
            jobInstanceDao,
            jobExecutionDao,
            stepExecutionDao,
            executionContextDao
        );
    }

    @Bean
    @Primary
    JobOperator jobOperator(
            JobRepository jobRepository) throws Exception {
        
        MapJobRegistry jobRegistry = new MapJobRegistry();
        
        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        operator.setJobRepository(jobRepository);
        operator.setJobRegistry(jobRegistry);
        operator.afterPropertiesSet();
        return operator;
    }
}
