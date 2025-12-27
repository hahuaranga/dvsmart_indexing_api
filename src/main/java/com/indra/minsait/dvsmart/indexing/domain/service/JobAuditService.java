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
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.io.PrintWriter;
import java.io.StringWriter;

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
            // 1. Buscar el registro existente por jobExecutionId
            Optional<JobExecutionAuditDocument> existingAudit = 
                auditRepository.findByJobExecutionId(jobExecution.getId());
            
            if (existingAudit.isEmpty()) {
                log.warn("Audit record not found for job execution: {}. Cannot update.", jobExecution.getId());
                return;
            }
            
            // 2. Actualizar el documento existente
            JobExecutionAuditDocument auditDoc = existingAudit.get();
            
            // Actualizar campos
            auditDoc.setStatus(jobExecution.getStatus().name());
            auditDoc.setExitCode(jobExecution.getExitStatus().getExitCode());
            auditDoc.setExitDescription(jobExecution.getExitStatus().getExitDescription());
            // Convertir LocalDateTime a Instant
            auditDoc.setEndTime(
                jobExecution.getEndTime() != null 
                    ? jobExecution.getEndTime().atZone(ZoneId.systemDefault()).toInstant()
                    : null
            );
            
            // Calcular duración
            if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
                long durationMs = Duration.between(
                        jobExecution.getStartTime(), 
                        jobExecution.getEndTime()
                    ).toMillis();
                auditDoc.setDurationMs(durationMs);
                auditDoc.setDurationFormatted(formatDuration(durationMs));
            }
            
            // Obtener métricas del step
            Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
            if (!stepExecutions.isEmpty()) {
                StepExecution stepExecution = stepExecutions.iterator().next();
                
                auditDoc.setReadCount(stepExecution.getReadCount());
                auditDoc.setWriteCount(stepExecution.getWriteCount());
                auditDoc.setCommitCount(stepExecution.getCommitCount());
                auditDoc.setRollbackCount(stepExecution.getRollbackCount());
                
                auditDoc.setTotalFilesIndexed(stepExecution.getWriteCount());
                auditDoc.setTotalFilesProcessed(stepExecution.getReadCount());
                auditDoc.setTotalFilesSkipped(stepExecution.getReadSkipCount() + stepExecution.getProcessSkipCount());
                auditDoc.setTotalFilesFailed(stepExecution.getWriteSkipCount() + stepExecution.getRollbackCount());
                
                // Calcular throughput (filesPerSecond)
                if (auditDoc.getDurationMs() != null && auditDoc.getDurationMs() > 0) {
                    double seconds = auditDoc.getDurationMs() / 1000.0;
                    double filesPerSecond = stepExecution.getWriteCount() / seconds;
                    auditDoc.setFilesPerSecond(filesPerSecond);
                }
            }
            
            // Capturar errores si existen
            if (jobExecution.getStatus() == BatchStatus.FAILED) {
                List<Throwable> failureExceptions = jobExecution.getFailureExceptions();
                if (!failureExceptions.isEmpty()) {
                    Throwable firstException = failureExceptions.get(0);
                    auditDoc.setErrorDescription(firstException.getMessage());
                    auditDoc.setErrorStackTrace(getStackTraceAsString(firstException, 10));
                }
                auditDoc.setFailureCount(failureExceptions.size());
            }
            
            // 3. Guardar (ahora hará UPDATE porque tiene _id)
            auditRepository.save(auditDoc);
            
            log.info("✅ Audit record updated: auditId={}, status={}, filesIndexed={}, duration={}", 
                auditDoc.getAuditId(), 
                auditDoc.getStatus(), 
                auditDoc.getTotalFilesIndexed(),
                auditDoc.getDurationFormatted());
                
        } catch (Exception e) {
            log.error("Failed to update audit record for job execution: {}", jobExecution.getId(), e);
        }
    }
    
    // ========================================
    // MÉTODOS PRIVADOS - LÓGICA DE DOMINIO
    // ========================================
    
    /**
     * Convierte el stack trace de una excepción a String, limitado a N líneas
     */
    private String getStackTraceAsString(Throwable throwable, int maxLines) {
        if (throwable == null) {
            return null;
        }
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        
        String fullStackTrace = sw.toString();
        
        // Limitar a las primeras N líneas
        String[] lines = fullStackTrace.split("\n");
        int linesToTake = Math.min(maxLines, lines.length);
        
        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < linesToTake; i++) {
            truncated.append(lines[i]);
            if (i < linesToTake - 1) {
                truncated.append("\n");
            }
        }
        
        if (lines.length > maxLines) {
            truncated.append("\n... (").append(lines.length - maxLines).append(" more lines)");
        }
        
        return truncated.toString();
    }    
    
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
    
}