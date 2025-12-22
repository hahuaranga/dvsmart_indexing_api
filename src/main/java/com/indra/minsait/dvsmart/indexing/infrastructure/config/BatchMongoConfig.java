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
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 22-12-2025 at 01:20:59
 * File: BatchMongoConfig.java
 */

/**
 * Configuración de infraestructura Spring Batch usando MongoDB
 * como repositorio de metadatos.
 *
 * ✅ FIX: Custom converters para forzar Long en secuencias
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
     * ✅ CRITICAL FIX: Custom Converters para Integer → Long
     * 
     * Problema: MongoDB BSON serializa números pequeños como Integer,
     * pero Spring Batch espera Long en las secuencias.
     * 
     * Solución: Converter que fuerza todo a Long en lectura.
     */
    @Bean
    MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        
        // Converter que lee Integer y lo convierte a Long
        converters.add(new IntegerToLongConverter());
        
        // Converter que escribe siempre Long (no Integer)
        converters.add(new LongToLongConverter());
        
        return new MongoCustomConversions(converters);
    }

    /**
     * ✅ Converter de lectura: Integer → Long
     * Se ejecuta cuando MongoDB retorna un NumberInt pero esperamos Long
     */
    @ReadingConverter
    static class IntegerToLongConverter implements Converter<Integer, Long> {
        @Override
        public Long convert(Integer source) {
            return source.longValue();
        }
    }

    /**
     * ✅ Converter de escritura: Long → Long (forzar tipo)
     * Asegura que los Long se escriban como NumberLong en BSON
     */
    @WritingConverter
    static class LongToLongConverter implements Converter<Long, Long> {
        @Override
        public Long convert(Long source) {
            return source; // Fuerza el tipo explícito
        }
    }

    /**
     * MongoTemplate con custom converters y soporte para ExecutionContext
     */
    @Bean
    MongoTemplate batchMongoTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter converter) {

        MappingMongoConverter mappingConverter = (MappingMongoConverter) converter;

        // OBLIGATORIO para Spring Batch (evita problemas con '.' en keys)
        mappingConverter.setMapKeyDotReplacement("_");
        
        // ✅ Aplicar custom converters
        mappingConverter.setCustomConversions(customConversions());
        mappingConverter.afterPropertiesSet();

        return new MongoTemplate(databaseFactory, mappingConverter);
    }

    /**
     * TransactionManager requerido por Spring Batch
     */
    @Bean
    ResourcelessTransactionManager transactionManager() {
        return new ResourcelessTransactionManager();
    }    

    /**
     * JobRepository oficial de Spring Batch para MongoDB
     */
    @Bean
    JobRepository jobRepository(
            MongoTemplate batchMongoTemplate,
            ResourcelessTransactionManager transactionManager) throws Exception {

        MongoJobRepositoryFactoryBean factory = new MongoJobRepositoryFactoryBean();
        factory.setMongoOperations(batchMongoTemplate);
        factory.setTransactionManager(transactionManager);
        
        // ✅ NUEVO: Asegurar que las secuencias se inicialicen correctamente
        factory.afterPropertiesSet();

        return factory.getObject();
    }
}