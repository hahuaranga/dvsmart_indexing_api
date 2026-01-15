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

import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.CustomLazySftpSessionFactory;
import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.SftpPoolMonitor;
import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.SftpPoolMonitor.ExtendedPoolStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 18-12-2025 at 16:29:47
 * File: MonitoringController.java
 */

/**
 * Endpoint para monitorear el estado del pool SFTP y Jobs de Spring Batch.
 * 
 * Endpoints disponibles:
 * 
 * SFTP Pool:
 * - GET  /api/monitoring/sftp-pool           - Estadísticas básicas
 * - GET  /api/monitoring/sftp-pool/extended  - Estadísticas extendidas
 * - GET  /api/monitoring/sftp-pool/health    - Estado de salud
 * - POST /api/monitoring/sftp-pool/evict     - Forzar limpieza
 * - POST /api/monitoring/sftp-pool/reset     - Reset contadores
 * - POST /api/monitoring/sftp-pool/log       - Log manual
 * 
 * Batch Jobs:
 * - GET  /api/monitoring/jobs                - Lista todos los jobs
 * - GET  /api/monitoring/jobs/running        - Jobs en ejecución
 * - GET  /api/monitoring/jobs/latest         - Última ejecución de cada job
 * - GET  /api/monitoring/jobs/{name}         - Historial de un job específico
 * - GET  /api/monitoring/jobs/execution/{id} - Detalle de una ejecución
 * - GET  /api/monitoring/jobs/stats          - Estadísticas globales
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final SftpPoolMonitor poolMonitor;
    private final JobRepository jobExplorer;

    /* ========================================
     * SFTP POOL MONITORING
     * ======================================== */

    @GetMapping("/sftp-pool")
    public ResponseEntity<Map<String, Object>> getSftpPoolStats() {
        var stats = poolMonitor.getStats();
        poolMonitor.logStats();
        
        return ResponseEntity.ok(Map.of(
            "active", stats.active(),
            "idle", stats.idle(),
            "maxTotal", stats.maxTotal(),
            "totalCreated", stats.created(),
            "totalDestroyed", stats.destroyed(),
            "utilizationPercent", calculateUtilization(stats),
            "availableSlots", stats.maxTotal() - stats.active()
        ));
    }

    @GetMapping("/sftp-pool/extended")
    public ResponseEntity<ExtendedPoolStats> getExtendedStats() {
        ExtendedPoolStats stats = poolMonitor.getExtendedStats();
        log.info("Extended stats requested: {}", stats.getHealthStatus());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/sftp-pool/health")
    public ResponseEntity<Map<String, Object>> getPoolHealth() {
        ExtendedPoolStats stats = poolMonitor.getExtendedStats();
        String healthStatus = stats.getHealthStatus();
        boolean isHealthy = stats.isHealthy();
        
        log.info("Pool health check: status={}, healthy={}", healthStatus, isHealthy);
        
        return ResponseEntity.ok(Map.of(
            "status", healthStatus,
            "healthy", isHealthy,
            "details", Map.of(
                "active", stats.active(),
                "idle", stats.idle(),
                "utilizationPercent", stats.utilizationPercent(),
                "totalFailures", stats.totalFailures(),
                "totalBorrows", stats.totalBorrows()
            )
        ));
    }

    @PostMapping("/sftp-pool/evict")
    public ResponseEntity<Map<String, Object>> forceEviction() {
        log.info("Manual eviction triggered");
        
        var statsBefore = poolMonitor.getStats();
        poolMonitor.forceEviction();
        var statsAfter = poolMonitor.getStats();
        
        return ResponseEntity.ok(Map.of(
            "message", "Eviction triggered",
            "before", Map.of(
                "active", statsBefore.active(),
                "idle", statsBefore.idle()
            ),
            "after", Map.of(
                "active", statsAfter.active(),
                "idle", statsAfter.idle()
            )
        ));
    }

    @PostMapping("/sftp-pool/reset")
    public ResponseEntity<Map<String, Object>> resetCounters() {
        log.warn("Pool monitor counters reset requested");
        poolMonitor.resetCounters();
        
        return ResponseEntity.ok(Map.of(
            "message", "Counters reset successfully",
            "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/sftp-pool/log")
    public ResponseEntity<Map<String, Object>> logStats() {
        poolMonitor.logStats();
        return ResponseEntity.ok(Map.of(
            "message", "Stats logged to server logs",
            "checkLogs", true
        ));
    }

    /* ========================================
     * BATCH JOBS MONITORING
     * ======================================== */

    /**
     * GET /api/monitoring/jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        List<String> jobNames = jobExplorer.getJobNames();
        
        log.info("📋 Jobs available: {}", jobNames);
        
        return ResponseEntity.ok(Map.of(
            "jobNames", jobNames,
            "totalJobs", jobNames.size()
        ));
    }

    /**
     * GET /api/monitoring/jobs/running
     */
    @GetMapping("/jobs/running")
    public ResponseEntity<Map<String, Object>> getRunningJobs() {
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(null);
        
        List<Map<String, Object>> runningJobs = runningExecutions.stream()
            .map(this::buildJobExecutionSummary)
            .collect(Collectors.toList());
        
        log.info("🏃 Running jobs: {}", runningJobs.size());
        
        return ResponseEntity.ok(Map.of(
            "runningJobs", runningJobs,
            "count", runningJobs.size()
        ));
    }

    /**
     * GET /api/monitoring/jobs/latest
     */
    @GetMapping("/jobs/latest")
    public ResponseEntity<Map<String, Object>> getLatestExecutions() {
        List<String> jobNames = jobExplorer.getJobNames();
        
        List<Map<String, Object>> latestExecutions = jobNames.stream()
            .map(jobName -> {
                List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 1);
                if (!instances.isEmpty()) {
                    JobInstance instance = instances.get(0);
                    List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
                    if (!executions.isEmpty()) {
                        JobExecution latest = executions.get(0);
                        return buildJobExecutionDetail(latest);
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        log.info("📊 Latest executions: {}", latestExecutions.size());
        
        return ResponseEntity.ok(Map.of(
            "latestExecutions", latestExecutions
        ));
    }

    /**
     * GET /api/monitoring/jobs/{jobName}
     * @throws NoSuchJobException 
     */
    @GetMapping("/jobs/{jobName}")
    public ResponseEntity<Map<String, Object>> getJobHistory(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) throws NoSuchJobException {
        
        List<JobInstance> instances = jobExplorer.getJobInstances(jobName, page, size);
        
        List<Map<String, Object>> executions = instances.stream()
            .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
            .sorted(Comparator.comparing(JobExecution::getCreateTime).reversed())
            .map(this::buildJobExecutionDetail)
            .collect(Collectors.toList());
        
        long totalCount = jobExplorer.getJobInstanceCount(jobName);
        
        log.info("📜 Job history for '{}': {} executions (page {}/{})", 
                 jobName, executions.size(), page, size);
        
        return ResponseEntity.ok(Map.of(
            "jobName", jobName,
            "executions", executions,
            "totalExecutions", totalCount,
            "page", page,
            "size", size
        ));
    }

    /**
     * GET /api/monitoring/jobs/execution/{executionId}
     */
    @GetMapping("/jobs/execution/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionDetail(@PathVariable Long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> detail = buildJobExecutionDetail(execution);
        
        // Agregar detalle de steps
        Collection<StepExecution> stepExecutions = execution.getStepExecutions();
        List<Map<String, Object>> steps = stepExecutions.stream()
            .map(this::buildStepExecutionDetail)
            .collect(Collectors.toList());
        
        detail.put("steps", steps);
        
        log.info("🔍 Execution detail requested: {}", executionId);
        
        return ResponseEntity.ok(detail);
    }

    /**
     * GET /api/monitoring/jobs/stats
     * @throws NoSuchJobException 
     */
    @GetMapping("/jobs/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() throws NoSuchJobException {
        List<String> jobNames = jobExplorer.getJobNames();
        
        Map<BatchStatus, Long> statusCounts = new HashMap<>();
        long totalExecutions = 0;
        long runningExecutions = 0;
        long completedExecutions = 0;
        long failedExecutions = 0;
        
        for (String jobName : jobNames) {
            long count = jobExplorer.getJobInstanceCount(jobName);
            totalExecutions += count;
            
            List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, (int)count);
            for (JobInstance instance : instances) {
                List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
                for (JobExecution execution : executions) {
                    BatchStatus status = execution.getStatus();
                    statusCounts.merge(status, 1L, Long::sum);
                    
                    if (status == BatchStatus.STARTED || status == BatchStatus.STARTING) {
                        runningExecutions++;
                    } else if (status == BatchStatus.COMPLETED) {
                        completedExecutions++;
                    } else if (status == BatchStatus.FAILED) {
                        failedExecutions++;
                    }
                }
            }
        }
        
        Map<String, Long> byStatus = statusCounts.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().name(),
                Map.Entry::getValue
            ));
        
        log.info("📈 Global stats: {} jobs, {} executions", jobNames.size(), totalExecutions);
        
        return ResponseEntity.ok(Map.of(
            "totalJobs", jobNames.size(),
            "totalExecutions", totalExecutions,
            "runningExecutions", runningExecutions,
            "completedExecutions", completedExecutions,
            "failedExecutions", failedExecutions,
            "byStatus", byStatus
        ));
    }

    /**
     * GET /api/monitoring/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        ExtendedPoolStats poolStats = poolMonitor.getExtendedStats();
        boolean poolHealthy = poolStats.isHealthy();
        
        Set<JobExecution> runningJobs = jobExplorer.findRunningJobExecutions(null);
        
        // ✅ CORRECCIÓN: Un job está saludable si NO está en estado FAILED
        // Jobs en STARTED/STARTING son normales (no son degraded)
        boolean jobsHealthy = runningJobs.stream()
            .allMatch(exec -> exec.getStatus() != BatchStatus.FAILED);
        
        // ✅ CORRECCIÓN: Sistema saludable si AMBOS componentes están OK
        boolean systemHealthy = poolHealthy && jobsHealthy;
        
        String batchStatus;
        if (runningJobs.isEmpty()) {
            batchStatus = "IDLE";  // ✅ Sin jobs corriendo = IDLE (no DEGRADED)
        } else if (jobsHealthy) {
            batchStatus = "RUNNING";  // ✅ Jobs corriendo sin fallos = RUNNING
        } else {
            batchStatus = "DEGRADED";  // ✅ Jobs con fallos = DEGRADED
        }
        
        return ResponseEntity.ok(Map.of(
            "status", systemHealthy ? "UP" : "DEGRADED",
            "components", Map.of(
                "sftp", Map.of(
                    "status", poolStats.getHealthStatus(),
                    "active", poolStats.active(),
                    "available", poolStats.maxTotal() - poolStats.active()
                ),
                "batch", Map.of(
                    "status", batchStatus,
                    "runningJobs", runningJobs.size()
                )
            )
        ));
    }

    /* ========================================
     * HELPER METHODS
     * ======================================== */

    private double calculateUtilization(CustomLazySftpSessionFactory.PoolStats stats) {
        if (stats.maxTotal() == 0) return 0.0;
        return (double) stats.active() / stats.maxTotal() * 100.0;
    }

    private Map<String, Object> buildJobExecutionSummary(JobExecution execution) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executionId", execution.getId());
        summary.put("jobName", execution.getJobInstance().getJobName());
        summary.put("status", execution.getStatus().name());
        summary.put("startTime", execution.getStartTime());
        
        if (execution.getEndTime() != null) {
            summary.put("endTime", execution.getEndTime());
            summary.put("duration", calculateDuration(execution));
        } else {
            summary.put("duration", calculateDuration(execution));
        }
        
        return summary;
    }

    private Map<String, Object> buildJobExecutionDetail(JobExecution execution) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("executionId", execution.getId());
        detail.put("jobName", execution.getJobInstance().getJobName());
        detail.put("status", execution.getStatus().name());
        detail.put("createTime", execution.getCreateTime());
        detail.put("startTime", execution.getStartTime());
        detail.put("endTime", execution.getEndTime());
        detail.put("duration", calculateDuration(execution));
        detail.put("exitCode", execution.getExitStatus().getExitCode());
        detail.put("exitDescription", execution.getExitStatus().getExitDescription());
        
        // ✅ CORRECTO: Iterar sobre parameters() que retorna Set<JobParameter<?>>
        Map<String, Object> params = new LinkedHashMap<>();
        execution.getJobParameters().parameters().forEach(jobParameter -> {
            params.put(jobParameter.name(), jobParameter.value());
        });
        detail.put("parameters", params);
        
        return detail;
    }

    private Map<String, Object> buildStepExecutionDetail(StepExecution step) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stepName", step.getStepName());
        detail.put("status", step.getStatus().name());
        detail.put("readCount", step.getReadCount());
        detail.put("writeCount", step.getWriteCount());
        detail.put("commitCount", step.getCommitCount());
        detail.put("rollbackCount", step.getRollbackCount());
        detail.put("readSkipCount", step.getReadSkipCount());
        detail.put("processSkipCount", step.getProcessSkipCount());
        detail.put("writeSkipCount", step.getWriteSkipCount());
        detail.put("filterCount", step.getFilterCount());
        detail.put("startTime", step.getStartTime());
        detail.put("endTime", step.getEndTime());
        detail.put("duration", calculateStepDuration(step));
        detail.put("exitCode", step.getExitStatus().getExitCode());
        
        return detail;
    }

    private String calculateDuration(JobExecution execution) {
        if (execution.getStartTime() == null) {
            return "N/A";
        }
        
        LocalDateTime endTime = execution.getEndTime() != null 
            ? execution.getEndTime()
            : LocalDateTime.now();
        
        Duration duration = Duration.between(
            execution.getStartTime(),
            endTime
        );
        
        return formatDuration(duration);
    }

    private String calculateStepDuration(StepExecution step) {
        if (step.getStartTime() == null) {
            return "N/A";
        }
        
        LocalDateTime endTime = step.getEndTime() != null 
            ? step.getEndTime()
            : LocalDateTime.now();
        
        Duration duration = Duration.between(
            step.getStartTime(),
            endTime
        );
        
        return formatDuration(duration);
    }

    private String formatDuration(Duration duration) {
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
}