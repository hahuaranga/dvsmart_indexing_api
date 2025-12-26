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
package com.indra.minsait.dvsmart.indexing.domain.service;

import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity.JobExecutionAuditDocument;
import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.repository.JobExecutionAuditRepository;
import com.indra.minsait.dvsmart.indexing.domain.model.JobExecutionAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 12:19:07
 * File: JobAuditService.java
 */

/**
 * Servicio de dominio para auditoría de ejecuciones de jobs.
 * ✅ Trabaja SOLO con modelos de dominio (JobExecutionAudit)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAuditService {
    
    private final JobExecutionAuditRepository auditRepository;
    
    @Value("${spring.application.name:dvsmart-indexing-api}")
    private String serviceName;
    
    /**
     * Crea un registro de auditoría al inicio del job.
     * ✅ RETORNA: Modelo de dominio
     */
    public String createAuditRecord(JobExecution jobExecution) {
        try {
            // ✅ 1. Crear modelo de dominio
            JobExecutionAudit audit = buildInitialAudit(jobExecution);
            
            // ✅ 2. Mapear a entidad de infraestructura
            JobExecutionAuditDocument document = toDocument(audit);
            
            // ✅ 3. Persistir
            auditRepository.save(document);
            
            log.info("✅ Audit record created: auditId={}, jobExecutionId={}", 
                     audit.getAuditId(), audit.getJobExecutionId());
            
            return audit.getAuditId();
            
        } catch (Exception e) {
            log.error("Failed to create audit record for job execution: {}", 
                      jobExecution.getId(), e);
            return null;
        }
    }
    
    /**
     * Actualiza el registro de auditoría al finalizar el job.
     */
    public void updateAuditRecord(JobExecution jobExecution) {
        try {
            Long jobExecutionId = jobExecution.getId();
            
            // ✅ 1. Obtener documento de BD
            JobExecutionAuditDocument document = auditRepository
                    .findByJobExecutionId(jobExecutionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Audit record not found for job execution: " + jobExecutionId));
            
            // ✅ 2. Mapear a modelo de dominio
            JobExecutionAudit audit = toDomain(document);
            
            // ✅ 3. Actualizar modelo de dominio
            updateAuditWithJobMetrics(audit, jobExecution);
            
            // ✅ 4. Mapear de vuelta a entidad
            document = toDocument(audit);
            
            // ✅ 5. Persistir
            auditRepository.save(document);
            
            log.info("✅ Audit record updated: auditId={}, status={}, filesIndexed={}, duration={}", 
                     audit.getAuditId(), 
                     audit.getStatus(), 
                     audit.getTotalFilesIndexed(),
                     audit.getDurationFormatted());
            
        } catch (Exception e) {
            log.error("Failed to update audit record for job execution: {}", 
                      jobExecution.getId(), e);
        }
    }
    
    // ========================================
    // MÉTODOS PRIVADOS - LÓGICA DE DOMINIO
    // ========================================
    
    /**
     * ✅ Construye el modelo de dominio inicial.
     */
    private JobExecutionAudit buildInitialAudit(JobExecution jobExecution) {
        String auditId = generateAuditId(jobExecution);
        
        return JobExecutionAudit.builder()
                .auditId(auditId)
                .jobExecutionId(jobExecution.getId())
                .serviceName(serviceName)
                .jobName(jobExecution.getJobInstance().getJobName())
                .startTime(toInstant(jobExecution.getStartTime()))
                .status(jobExecution.getStatus().name())
                .jobParameters(extractJobParameters(jobExecution))
                .hostname(getHostname())
                .instanceId(getInstanceId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
    
    /**
     * ✅ Actualiza el modelo de dominio con métricas del job.
     */
    private void updateAuditWithJobMetrics(JobExecutionAudit audit, JobExecution jobExecution) {
        // Calcular métricas
        StepExecution stepExecution = jobExecution.getStepExecutions().stream()
                .findFirst()
                .orElse(null);
        
        long totalFilesIndexed = 0;
        long totalFilesProcessed = 0;
        long totalFilesSkipped = 0;
        long totalFilesFailed = 0;
        long readCount = 0;
        long writeCount = 0;
        long commitCount = 0;
        long rollbackCount = 0;
        
        if (stepExecution != null) {
            readCount = stepExecution.getReadCount();
            writeCount = stepExecution.getWriteCount();
            commitCount = stepExecution.getCommitCount();
            rollbackCount = stepExecution.getRollbackCount();
            
            totalFilesProcessed = readCount;
            totalFilesIndexed = writeCount;
            totalFilesSkipped = stepExecution.getReadSkipCount() + 
                               stepExecution.getProcessSkipCount() + 
                               stepExecution.getFilterCount();
            totalFilesFailed = stepExecution.getWriteSkipCount() + rollbackCount;
        }
        
        // Calcular duración
        LocalDateTime endTimeLocal = jobExecution.getEndTime();
        Instant endTime = toInstant(endTimeLocal);
        Long durationMs = null;
        String durationFormatted = null;
        Double filesPerSecond = null;
        
        if (endTime != null && audit.getStartTime() != null) {
            durationMs = Duration.between(audit.getStartTime(), endTime).toMillis();
            durationFormatted = formatDuration(durationMs);
            
            if (durationMs > 0 && totalFilesIndexed > 0) {
                filesPerSecond = (double) totalFilesIndexed / (durationMs / 1000.0);
            }
        }
        
        // Actualizar modelo de dominio
        audit.setEndTime(endTime);
        audit.setDurationMs(durationMs);
        audit.setDurationFormatted(durationFormatted);
        audit.setStatus(jobExecution.getStatus().name());
        audit.setExitCode(jobExecution.getExitStatus().getExitCode());
        audit.setExitDescription(jobExecution.getExitStatus().getExitDescription());
        
        audit.setTotalFilesIndexed(totalFilesIndexed);
        audit.setTotalFilesProcessed(totalFilesProcessed);
        audit.setTotalFilesSkipped(totalFilesSkipped);
        audit.setTotalFilesFailed(totalFilesFailed);
        
        audit.setReadCount(readCount);
        audit.setWriteCount(writeCount);
        audit.setCommitCount(commitCount);
        audit.setRollbackCount(rollbackCount);
        audit.setFilesPerSecond(filesPerSecond);
        
        // Información de errores
        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            Throwable firstException = jobExecution.getAllFailureExceptions().get(0);
            audit.setErrorDescription(firstException.getMessage());
            audit.setErrorStackTrace(truncateStackTrace(firstException));
            audit.setFailureCount(jobExecution.getAllFailureExceptions().size());
        }
        
        audit.setUpdatedAt(Instant.now());
    }
    
    /**
     * Genera un ID único para auditoría.
     */
    private String generateAuditId(JobExecution jobExecution) {
        return String.format("%s-%d-%s",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Extrae parámetros del job como Map.
     */
    private Map<String, Object> extractJobParameters(JobExecution jobExecution) {
        Map<String, Object> params = new HashMap<>();
        
        jobExecution.getJobParameters().parameters().forEach(jobParameter -> {
            params.put(jobParameter.name(), jobParameter.value());
        });
        
        return params;
    }
    
    /**
     * Convierte LocalDateTime a Instant.
     */
    private Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
    
    /**
     * Obtiene el hostname del servidor.
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Obtiene el ID de la instancia (K8s pod name o hostname).
     */
    private String getInstanceId() {
        String podName = System.getenv("HOSTNAME");
        if (podName != null && !podName.isEmpty()) {
            return podName;
        }
        return getHostname();
    }
    
    /**
     * Formatea la duración en formato legible.
     */
    private String formatDuration(long durationMs) {
        Duration duration = Duration.ofMillis(durationMs);
        
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Trunca el stack trace para no saturar la BD.
     */
    private String truncateStackTrace(Throwable throwable) {
        if (throwable == null) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ")
          .append(throwable.getMessage()).append("\n");
        
        StackTraceElement[] elements = throwable.getStackTrace();
        int limit = Math.min(elements.length, 10);
        
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(elements[i].toString()).append("\n");
        }
        
        if (elements.length > limit) {
            sb.append("\t... ").append(elements.length - limit).append(" more");
        }
        
        String result = sb.toString();
        
        if (result.length() > 2000) {
            return result.substring(0, 1997) + "...";
        }
        
        return result;
    }
    
    // ========================================
    // MAPPERS - DOMINIO ↔ INFRAESTRUCTURA
    // ========================================
    
    /**
     * ✅ Mapea modelo de dominio → entidad MongoDB.
     */
    private JobExecutionAuditDocument toDocument(JobExecutionAudit audit) {
        return JobExecutionAuditDocument.builder()
                .auditId(audit.getAuditId())
                .jobExecutionId(audit.getJobExecutionId())
                .serviceName(audit.getServiceName())
                .jobName(audit.getJobName())
                .startTime(audit.getStartTime())
                .endTime(audit.getEndTime())
                .durationMs(audit.getDurationMs())
                .durationFormatted(audit.getDurationFormatted())
                .status(audit.getStatus())
                .exitCode(audit.getExitCode())
                .exitDescription(audit.getExitDescription())
                .totalFilesIndexed(audit.getTotalFilesIndexed())
                .totalFilesProcessed(audit.getTotalFilesProcessed())
                .totalFilesSkipped(audit.getTotalFilesSkipped())
                .totalFilesFailed(audit.getTotalFilesFailed())
                .totalDirectoriesProcessed(audit.getTotalDirectoriesProcessed())
                .readCount(audit.getReadCount())
                .writeCount(audit.getWriteCount())
                .commitCount(audit.getCommitCount())
                .rollbackCount(audit.getRollbackCount())
                .filesPerSecond(audit.getFilesPerSecond())
                .errorDescription(audit.getErrorDescription())
                .errorStackTrace(audit.getErrorStackTrace())
                .failureCount(audit.getFailureCount())
                .jobParameters(audit.getJobParameters())
                .hostname(audit.getHostname())
                .instanceId(audit.getInstanceId())
                .createdAt(audit.getCreatedAt())
                .updatedAt(audit.getUpdatedAt())
                .build();
    }
    
    /**
     * ✅ Mapea entidad MongoDB → modelo de dominio.
     */
    private JobExecutionAudit toDomain(JobExecutionAuditDocument document) {
        return JobExecutionAudit.builder()
                .auditId(document.getAuditId())
                .jobExecutionId(document.getJobExecutionId())
                .serviceName(document.getServiceName())
                .jobName(document.getJobName())
                .startTime(document.getStartTime())
                .endTime(document.getEndTime())
                .durationMs(document.getDurationMs())
                .durationFormatted(document.getDurationFormatted())
                .status(document.getStatus())
                .exitCode(document.getExitCode())
                .exitDescription(document.getExitDescription())
                .totalFilesIndexed(document.getTotalFilesIndexed())
                .totalFilesProcessed(document.getTotalFilesProcessed())
                .totalFilesSkipped(document.getTotalFilesSkipped())
                .totalFilesFailed(document.getTotalFilesFailed())
                .totalDirectoriesProcessed(document.getTotalDirectoriesProcessed())
                .readCount(document.getReadCount())
                .writeCount(document.getWriteCount())
                .commitCount(document.getCommitCount())
                .rollbackCount(document.getRollbackCount())
                .filesPerSecond(document.getFilesPerSecond())
                .errorDescription(document.getErrorDescription())
                .errorStackTrace(document.getErrorStackTrace())
                .failureCount(document.getFailureCount())
                .jobParameters(document.getJobParameters())
                .hostname(document.getHostname())
                .instanceId(document.getInstanceId())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}