/*
 * /////////////////////////////////////////////////////////////////////////////
 *
 * Copyright (c) 2026 Indra Sistemas, S.A. All Rights Reserved.
 * http://www.indracompany.com/
 *
 * The contents of this file are owned by Indra Sistemas, S.A. copyright holder.
 * This file can only be copied, distributed and used all or in part with the
 * written permission of Indra Sistemas, S.A, or in accordance with the terms and
 * conditions laid down in the agreement / contract under which supplied.
 *
 * /////////////////////////////////////////////////////////////////////////////
 */
package com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 12:14:13
 * File: JobExecutionAuditDocument.java
 */

/**
 * Documento MongoDB para auditoría de ejecuciones de jobs.
 */
@Data
@Builder
@Document(collection = "job_executions_audit")
public class JobExecutionAuditDocument {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String auditId;
    
    @Indexed
    private Long jobExecutionId;
    
    // Información del servicio y job
    private String serviceName;
    
    @Indexed
    private String jobName;
    
    // Tiempos de ejecución
    @Indexed
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String durationFormatted;
    
    // Estado y resultados
    @Indexed
    private String status;
    private String exitCode;
    private String exitDescription;
    
    // Métricas de procesamiento
    private Long totalFilesIndexed;
    private Long totalFilesProcessed;
    private Long totalFilesSkipped;
    private Long totalFilesFailed;
    private Long totalDirectoriesProcessed;
    
    // Métricas de rendimiento
    private Long readCount;
    private Long writeCount;
    private Long commitCount;
    private Long rollbackCount;
    private Double filesPerSecond;
    
    // Información de errores
    private String errorDescription;
    private String errorStackTrace;
    private Integer failureCount;
    
    // Parámetros del job
    private Map<String, Object> jobParameters;
    
    // Información del servidor
    private String hostname;
    private String instanceId;
    
    // Auditoría
    private Instant createdAt;
    private Instant updatedAt;
}
