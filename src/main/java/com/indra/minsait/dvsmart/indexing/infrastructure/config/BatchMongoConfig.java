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
import org.springframework.batch.core.repository.support.MongoJobRepositoryFactoryBean;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 01:20:59
 * File: BatchMongoConfig.java
 */

/**
 * Configuración de infraestructura Spring Batch usando MongoDB
 * como repositorio de metadatos.
 *
 * Compatible con:
 * - Spring Boot 4.0.0
 * - Spring Batch 6.0.0
 * - Java 21
 */
@Configuration
@EnableBatchProcessing
public class BatchMongoConfig {

    /**
     * MongoTemplate con soporte para ExecutionContext de Spring Batch
     * (MongoDB no permite '.' en keys).
     */
    @Bean
    MongoTemplate batchMongoTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter converter) {

        MappingMongoConverter mappingConverter =
                (MappingMongoConverter) converter;

        // OBLIGATORIO para Spring Batch
        mappingConverter.setMapKeyDotReplacement("_");

        return new MongoTemplate(databaseFactory, mappingConverter);
    }

    /**
     * TransactionManager requerido por Spring Batch.
     * MongoDB soporta transacciones a nivel de documento/colección.
     */
//    @Bean
//    MongoTransactionManager batchTransactionManager(
//            MongoDatabaseFactory databaseFactory) {
//        return new MongoTransactionManager(databaseFactory);
//    }
    @Bean
    ResourcelessTransactionManager transactionManager() {
        return new ResourcelessTransactionManager();
    }    

    /**
     * JobRepository oficial de Spring Batch para MongoDB.
     * Este bean es EL CORAZÓN de la integración.
     */
    @Bean
    JobRepository jobRepository(
            MongoTemplate batchMongoTemplate,
            MongoTransactionManager transactionManager) throws Exception {

        MongoJobRepositoryFactoryBean factory =
                new MongoJobRepositoryFactoryBean();

        factory.setMongoOperations(batchMongoTemplate);
        factory.setTransactionManager(transactionManager);

        factory.afterPropertiesSet();

        return factory.getObject();
    }
}