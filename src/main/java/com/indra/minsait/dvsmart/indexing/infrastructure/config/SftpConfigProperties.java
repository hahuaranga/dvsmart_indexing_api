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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 11:03:22
 * File: SftpConfigProperties.java
 */

@Getter
@Setter
@ConfigurationProperties(prefix = "sftp")
public class SftpConfigProperties {
    
    private Origin origin = new Origin();
    
    @Getter
    @Setter
    public static class Origin {
        private String host;
        private int port = 22;
        private String user;
        private String password;
        private String baseDir;
        private int timeout = 30000;
        private Pool pool = new Pool();
    }
    
    @Getter
    @Setter
    public static class Pool {
        // Tamaño del pool
        private int size = 10;
        private int maxSize = 10;
        private int initialSize = 0;
        
        // Configuración lazy
        private boolean lazyInit = true;
        
        // Timeouts y validación
        private long maxWaitMillis = 30000;
        private boolean testOnBorrow = true;
        private boolean testWhileIdle = true;
        
        // Eviction (limpieza de conexiones inactivas)
        private long timeBetweenEvictionRunsMillis = 60000;
        private long minEvictableIdleTimeMillis = 300000; // 5 minutos
    }
}