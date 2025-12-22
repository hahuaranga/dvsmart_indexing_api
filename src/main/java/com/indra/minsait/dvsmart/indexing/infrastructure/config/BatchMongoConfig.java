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

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.dao.mongodb.MongoExecutionContextDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoJobExecutionDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoJobInstanceDao;
import org.springframework.batch.core.repository.dao.mongodb.MongoStepExecutionDao;
import org.springframework.batch.core.repository.support.SimpleJobRepository;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 01:20:59
 * File: BatchMongoConfig.java
 */

/**
 * SOLUCIÓN SIMPLE Y DIRECTA: SimpleJobRepository con DAOs custom
 */
@Configuration
@EnableBatchProcessing
public class BatchMongoConfig {

    private static final String SEQUENCES_COLLECTION = "BATCH_SEQUENCES";
    private static final String JOB_INSTANCE_SEQ = "BATCH_JOB_INSTANCE_SEQ";
    private static final String JOB_EXECUTION_SEQ = "BATCH_JOB_EXECUTION_SEQ";
    private static final String STEP_EXECUTION_SEQ = "BATCH_STEP_EXECUTION_SEQ";

    @Bean
    MongoTemplate batchMongoTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter converter) {

        MappingMongoConverter mappingConverter = (MappingMongoConverter) converter;
        mappingConverter.setMapKeyDotReplacement("_");
        mappingConverter.afterPropertiesSet();
        return new MongoTemplate(databaseFactory, mappingConverter);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
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
    MongoJobInstanceDao jobInstanceDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer jobInstanceIncrementer) {
        
        MongoJobInstanceDao dao = new MongoJobInstanceDao(batchMongoTemplate);
        dao.setJobInstanceIncrementer(jobInstanceIncrementer);
        return dao;
    }

    @Bean
    MongoJobExecutionDao jobExecutionDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer jobExecutionIncrementer) {
        
        MongoJobExecutionDao dao = new MongoJobExecutionDao(batchMongoTemplate);
        dao.setJobExecutionIncrementer(jobExecutionIncrementer);
        return dao;
    }

    @Bean
    MongoStepExecutionDao stepExecutionDao(
            MongoTemplate batchMongoTemplate,
            FixedMongoSequenceIncrementer stepExecutionIncrementer) {
        
        MongoStepExecutionDao dao = new MongoStepExecutionDao(batchMongoTemplate);
        dao.setStepExecutionIncrementer(stepExecutionIncrementer);
        return dao;
    }

    @Bean
    MongoExecutionContextDao executionContextDao(MongoTemplate batchMongoTemplate) {
        return new MongoExecutionContextDao(batchMongoTemplate);
    }

    /**
     * SimpleJobRepository directamente - sin FactoryBean
     */
    @Bean
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
}