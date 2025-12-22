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
 * ✅ SOLUCIÓN FINAL: Custom Converter a nivel de BSON
 * 
 * Convertimos Integer → Long en la capa de deserialización de BSON,
 * antes de que llegue a Spring Batch.
 */
@Configuration
@EnableBatchProcessing
public class BatchMongoConfig {

    /**
     * ✅ CRÍTICO: Converter que transforma Integer → Long en lectura
     */
    @ReadingConverter
    static class IntegerToLongConverter implements Converter<Integer, Long> {
        @Override
        public Long convert(Integer source) {
            return source.longValue();
        }
    }

    /**
     * ✅ Custom conversions con nuestro converter
     */
    @Bean
    MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new IntegerToLongConverter());
        return new MongoCustomConversions(converters);
    }

    /**
     * MongoTemplate con custom conversions y configuración para Spring Batch
     */
    @Bean
    MongoTemplate batchMongoTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter converter) {

        MappingMongoConverter mappingConverter = (MappingMongoConverter) converter;
        
        // ✅ OBLIGATORIO para Spring Batch: Reemplazar '.' en keys del ExecutionContext
        // Sin esto, MongoDB rechaza documentos con keys como "step.name"
        mappingConverter.setMapKeyDotReplacement("_");
        
        // ✅ Aplicar custom conversions (Integer → Long)
        mappingConverter.setCustomConversions(customConversions());
        
        // ✅ CRÍTICO: Re-inicializar después de cambios
        // afterPropertiesSet() debe llamarse DESPUÉS de setCustomConversions y setMapKeyDotReplacement
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
     * JobRepository usando MongoJobRepositoryFactoryBean estándar
     */
    @Bean
    JobRepository jobRepository(
            MongoTemplate batchMongoTemplate,
            ResourcelessTransactionManager transactionManager) throws Exception {

        MongoJobRepositoryFactoryBean factory = new MongoJobRepositoryFactoryBean();
        factory.setMongoOperations(batchMongoTemplate);
        factory.setTransactionManager(transactionManager);
        factory.afterPropertiesSet();

        return factory.getObject();
    }
}