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
package com.indra.minsait.dvsmart.indexing.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 11:59:53
 * File: JobExecutionAudit.java
 */

/**
 * Modelo de dominio para auditoría de ejecuciones de jobs.
 */
@Data
@Builder
public class JobExecutionAudit {
    
    // Identificación
    private String auditId;                    // ID único de auditoría
    private Long jobExecutionId;               // ID de Spring Batch
    
    // Información del servicio y job
    private String serviceName;                // Nombre del microservicio
    private String jobName;                    // Nombre del job (BATCH-INDEX-FULL)
    
    // Tiempos de ejecución
    private Instant startTime;                 // Fecha/hora de inicio
    private Instant endTime;                   // Fecha/hora de fin
    private Long durationMs;                   // Duración en milisegundos
    private String durationFormatted;          // Duración formateada (ej: "29m 55s")
    
    // Estado y resultados
    private String status;                     // STARTED, COMPLETED, FAILED, STOPPED
    private String exitCode;                   // COMPLETED, FAILED, UNKNOWN
    private String exitDescription;            // Descripción del resultado
    
    // Métricas de procesamiento
    private Long totalFilesIndexed;            // Total de archivos indexados
    private Long totalFilesProcessed;          // Total procesados (incluye skipped)
    private Long totalFilesSkipped;            // Total de archivos saltados
    private Long totalFilesFailed;             // Total de archivos fallidos
    private Long totalDirectoriesProcessed;    // Total de directorios procesados
    
    // Métricas de rendimiento
    private Long readCount;                    // Lecturas totales
    private Long writeCount;                   // Escrituras totales
    private Long commitCount;                  // Commits totales
    private Long rollbackCount;                // Rollbacks totales
    private Double filesPerSecond;             // Throughput (archivos/segundo)
    
    // Información de errores
    private String errorDescription;           // Descripción del error principal
    private String errorStackTrace;            // Stack trace (opcional, truncado)
    private Integer failureCount;              // Número de fallos durante ejecución
    
    // Parámetros del job
    private Map<String, Object> jobParameters; // Parámetros de entrada
    
    // Información del servidor
    private String hostname;                   // Host donde se ejecutó
    private String instanceId;                 // ID de la instancia (K8s pod)
    
    // Auditoría
    private Instant createdAt;                 // Cuándo se creó el registro
    private Instant updatedAt;                 // Última actualización
}
