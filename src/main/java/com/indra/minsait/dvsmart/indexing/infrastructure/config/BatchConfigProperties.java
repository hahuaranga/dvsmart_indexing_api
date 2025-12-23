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
 * Created on: 12-12-2025 at 12:25:45
 * File: BatchConfigProperties.java
 */

@Getter
@Setter
@ConfigurationProperties(prefix = "batch")
public class BatchConfigProperties {
    private int chunkSize = 100;
    //private int concurrencyLimit = 10;
    private int threadPoolSize = 20;
    private int queueCapacity = 1000;
    private int retryLimit = 3;
    private int skipLimit = 5;
}
