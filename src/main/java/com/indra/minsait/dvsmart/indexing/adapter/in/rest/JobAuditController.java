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
package com.indra.minsait.dvsmart.indexing.adapter.in.rest;

import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity.JobExecutionAuditDocument;
import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.repository.JobExecutionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 12:32:07
 * File: JobAuditController.java
 */

/**
 * Endpoints para consultar auditoría de ejecuciones de jobs.
 * # Obtener historial de un job
	curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/jobs/BATCH-INDEX-FULL | jq
	
	# Obtener ejecuciones completadas
	curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/status/COMPLETED | jq
	
	# Obtener auditoría de una ejecución específica
	curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/execution/12345 | jq
	
	# Obtener estadísticas globales
	curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/stats | jq
	
	# Obtener últimas ejecuciones
	curl http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/latest | jq
	
	# Obtener ejecuciones en un rango de fechas
	curl "http://localhost:8080/dvsmart_indexing_api/api/monitoring/audit/range?start=2025-12-01T00:00:00Z&end=2025-12-31T23:59:59Z" | jq
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring/audit")
@RequiredArgsConstructor
public class JobAuditController {
    
    private final JobExecutionAuditRepository auditRepository;
    
    /**
     * GET /api/monitoring/audit/jobs/{jobName}
     * Obtiene el historial de auditoría de un job específico.
     */
    @GetMapping("/jobs/{jobName}")
    public ResponseEntity<List<JobExecutionAuditDocument>> getJobAuditHistory(@PathVariable String jobName) {
        log.info("Fetching audit history for job: {}", jobName);
        List<JobExecutionAuditDocument> audits = auditRepository.findByJobNameOrderByStartTimeDesc(jobName);
        return ResponseEntity.ok(audits);
    }
    
    /**
     * GET /api/monitoring/audit/status/{status}
     * Obtiene ejecuciones por estado.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<JobExecutionAuditDocument>> getByStatus(@PathVariable String status) {
        log.info("Fetching audits with status: {}", status);
        List<JobExecutionAuditDocument> audits = auditRepository.findByStatusOrderByStartTimeDesc(status);
        return ResponseEntity.ok(audits);
    }
    
    /**
     * GET /api/monitoring/audit/execution/{jobExecutionId}
     * Obtiene auditoría de una ejecución específica.
     */
    @GetMapping("/execution/{jobExecutionId}")
    public ResponseEntity<JobExecutionAuditDocument> getByExecutionId(@PathVariable Long jobExecutionId) {
        log.info("Fetching audit for job execution: {}", jobExecutionId);
        return auditRepository.findByJobExecutionId(jobExecutionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * GET /api/monitoring/audit/range?start=...&end=...
     * Obtiene ejecuciones en un rango de fechas.
     */
    @GetMapping("/range")
    public ResponseEntity<List<JobExecutionAuditDocument>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        log.info("Fetching audits between {} and {}", start, end);
        List<JobExecutionAuditDocument> audits = auditRepository.findByStartTimeBetweenOrderByStartTimeDesc(start, end);
        return ResponseEntity.ok(audits);
    }
    
    /**
     * GET /api/monitoring/audit/stats
     * Obtiene estadísticas globales de auditoría.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        log.info("Fetching global audit stats");
        
        long totalExecutions = auditRepository.count();
        long completedExecutions = auditRepository.countByJobNameAndStatus("BATCH-INDEX-FULL", "COMPLETED");
        long failedExecutions = auditRepository.countByJobNameAndStatus("BATCH-INDEX-FULL", "FAILED");
        long startedExecutions = auditRepository.countByJobNameAndStatus("BATCH-INDEX-FULL", "STARTED");
        
        return ResponseEntity.ok(Map.of(
            "totalExecutions", totalExecutions,
            "completedExecutions", completedExecutions,
            "failedExecutions", failedExecutions,
            "startedExecutions", startedExecutions
        ));
    }
    
    /**
     * GET /api/monitoring/audit/latest
     * Obtiene la última ejecución de cada job.
     */
    @GetMapping("/latest")
    public ResponseEntity<List<JobExecutionAuditDocument>> getLatestExecutions() {
        log.info("Fetching latest executions");
        // Simplificado: devuelve últimas 10 ejecuciones de BATCH-INDEX-FULL
        List<JobExecutionAuditDocument> audits = auditRepository.findByJobNameOrderByStartTimeDesc("BATCH-INDEX-FULL")
                .stream()
                .limit(10)
                .toList();
        return ResponseEntity.ok(audits);
    }
}
