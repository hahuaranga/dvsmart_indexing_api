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
package com.indra.minsait.dvsmart.indexing.infrastructure.sftp;

import com.indra.minsait.dvsmart.indexing.infrastructure.config.SftpConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import jakarta.annotation.PreDestroy;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 13:05:17
 * File: SftpSessionFactoryConfig.java
 */

/**
 * Configuración de SessionFactory con pool lazy y validación.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SftpSessionFactoryConfig {

    private final SftpConfigProperties props;
    private CustomLazySftpSessionFactory lazyPoolFactory;

    /**
     * Factory base (sin pool) que crea conexiones SFTP individuales.
     */
    private SessionFactory<SftpClient.DirEntry> createBaseSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(props.getOrigin().getHost());
        factory.setPort(props.getOrigin().getPort());
        factory.setUser(props.getOrigin().getUser());
        factory.setPassword(props.getOrigin().getPassword());
        factory.setTimeout(props.getOrigin().getTimeout());
        factory.setAllowUnknownKeys(true);
        
        log.info("Base SFTP SessionFactory configured for {}:{}",
                props.getOrigin().getHost(),
                props.getOrigin().getPort());
        
        return factory;
    }

    /**
     * SessionFactory con pool lazy personalizado.
     * 
     * Ventajas:
     * - Conexiones creadas bajo demanda
     * - Validación pre-uso automática
     * - Eviction de conexiones inactivas
     * - Coordinación con otras APIs vía uso responsable
     */
    @Bean(name = "sftpOriginSessionFactory")
    SessionFactory<SftpClient.DirEntry> sftpOriginSessionFactory() {
        
        SessionFactory<SftpClient.DirEntry> baseFactory = createBaseSessionFactory();
        
        SftpConfigProperties.Pool poolConfig = props.getOrigin().getPool();
        
        lazyPoolFactory = new CustomLazySftpSessionFactory(
            baseFactory,
            poolConfig.getMaxSize(),
            poolConfig.getInitialSize(),
            poolConfig.getMaxWaitMillis(),
            poolConfig.isTestOnBorrow(),
            poolConfig.getTimeBetweenEvictionRunsMillis(),
            poolConfig.getMinEvictableIdleTimeMillis()
        );
        
        log.info("Lazy SFTP Session Pool initialized with max size: {}", poolConfig.getMaxSize());
        
        return lazyPoolFactory;
    }

    /**
     * Template para operaciones SFTP de alto nivel.
     */
    @Bean(name = "sftpOriginTemplate")
    SftpRemoteFileTemplate sftpOriginTemplate() {
        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sftpOriginSessionFactory());
        log.info("SFTP RemoteFileTemplate configured");
        return template;
    }

    /**
     * Monitor del pool como bean independiente.
     */
    @Bean
    SftpPoolMonitor sftpPoolMonitor() {
        return new SftpPoolMonitor(lazyPoolFactory);
    }

    /**
     * Limpieza al apagar la aplicación.
     */
    @PreDestroy
    public void cleanup() {
        if (lazyPoolFactory != null) {
            log.info("Shutting down SFTP session pool...");
            lazyPoolFactory.destroy();
        }
    }
}