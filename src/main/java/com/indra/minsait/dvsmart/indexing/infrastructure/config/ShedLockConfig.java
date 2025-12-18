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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 17-12-2025 at 23:34:40
 * File: ShedLockConfig.java
 */

/**
 * Configuración de ShedLock para coordinación distribuida.
 * 
 * Propósito:
 * - Evitar ejecuciones concurrentes del mismo job
 * - Coordinar entre dvsmart_indexing_api y dvsmart_reorganization_api
 * - Prevenir saturación del servidor SFTP origen
 * 
 * Funcionamiento:
 * - Usa MongoDB como backend de locks
 * - Lock automático al ejecutar métodos anotados con @SchedulerLock
 * - Si otro proceso tiene el lock, espera o falla según configuración
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT2H", defaultLockAtLeastFor = "PT30M")
@RequiredArgsConstructor
public class ShedLockConfig {

	private final MongoTemplate mongoTemplate;
	
    /**
     * Provider de locks usando MongoDB.
     * 
     * Comparte la misma colección con dvsmart_reorganization_api
     * para coordinación efectiva.
     */
    @Bean
    LockProvider lockProvider() {
        
        log.info("Configuring ShedLock with MongoDB provider");
        
        // Usar colección específica para locks
        // IMPORTANTE: Debe ser la misma que use dvsmart_reorganization_api
        LockProvider provider = new MongoLockProvider(mongoTemplate.getDb()
        );
        
        log.info("ShedLock provider configured successfully");
        
        return provider;
    }
}