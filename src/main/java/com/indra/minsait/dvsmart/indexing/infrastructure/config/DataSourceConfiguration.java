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

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 24-12-2025 at 13:44:48
 * File: DataSourceConfiguration.java
 */

/**
 * Configuración de DataSources.
 * 
 * - PRIMARY: PostgreSQL (Spring Batch metadata)
 * - MongoDB: Configurado automáticamente por Spring Boot
 */
@Configuration
public class DataSourceConfiguration {
    
    /**
     * DataSource PRINCIPAL para Spring Batch (PostgreSQL)
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties batchDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource batchDataSource() {
        return batchDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }
}
