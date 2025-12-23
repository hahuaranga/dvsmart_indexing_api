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
package com.indra.minsait.dvsmart.indexing.adapter.in.rest;

import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.CustomLazySftpSessionFactory;
import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.SftpPoolMonitor;
import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.SftpPoolMonitor.ExtendedPoolStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 18-12-2025 at 16:29:47
 * File: MonitoringController.java
 */

/**
 * Endpoint para monitorear el estado del pool SFTP y otros componentes.
 * 
 * Endpoints disponibles:
 * - GET  /api/monitoring/sftp-pool           - Estadísticas básicas
 * - GET  /api/monitoring/sftp-pool/extended  - Estadísticas extendidas
 * - GET  /api/monitoring/sftp-pool/health    - Estado de salud
 * - POST /api/monitoring/sftp-pool/evict     - Forzar limpieza
 * - POST /api/monitoring/sftp-pool/reset     - Reset contadores
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final SftpPoolMonitor poolMonitor;

    /**
     * Retorna estadísticas básicas del pool SFTP.
     * 
     * GET /api/monitoring/sftp-pool
     * 
     * Ejemplo response:
     * {
     *   "active": 2,
     *   "idle": 3,
     *   "maxTotal": 10,
     *   "totalCreated": 5,
     *   "totalDestroyed": 0,
     *   "utilizationPercent": 20.0,
     *   "availableSlots": 8
     * }
     */
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

    /**
     * Retorna estadísticas extendidas del pool.
     * 
     * GET /api/monitoring/sftp-pool/extended
     * 
     * Incluye métricas adicionales como borrows, returns, failures.
     */
    @GetMapping("/sftp-pool/extended")
    public ResponseEntity<ExtendedPoolStats> getExtendedStats() {
        
        ExtendedPoolStats stats = poolMonitor.getExtendedStats();
        
        log.info("Extended stats requested: {}", stats.getHealthStatus());
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check específico del pool SFTP.
     * 
     * GET /api/monitoring/sftp-pool/health
     * 
     * Response:
     * {
     *   "status": "HEALTHY" | "WARNING" | "DEGRADED" | "CRITICAL",
     *   "healthy": true | false,
     *   "details": { ... }
     * }
     */
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

    /**
     * Health check general del sistema.
     * 
     * GET /api/monitoring/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        
        ExtendedPoolStats stats = poolMonitor.getExtendedStats();
        boolean isHealthy = stats.isHealthy();
        
        return ResponseEntity.ok(Map.of(
            "status", isHealthy ? "UP" : "DEGRADED",
            "components", Map.of(
                "sftp", Map.of(
                    "status", stats.getHealthStatus(),
                    "active", stats.active(),
                    "available", stats.maxTotal() - stats.active()
                )
            )
        ));
    }

    /**
     * Forzar limpieza de conexiones inactivas.
     * 
     * POST /api/monitoring/sftp-pool/evict
     * 
     * Útil para mantenimiento o troubleshooting.
     */
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

    /**
     * Reset de contadores del monitor.
     * 
     * POST /api/monitoring/sftp-pool/reset
     * 
     * Útil después de mantenimiento o para testing.
     */
    @PostMapping("/sftp-pool/reset")
    public ResponseEntity<Map<String, Object>> resetCounters() {
        
        log.warn("Pool monitor counters reset requested");
        
        poolMonitor.resetCounters();
        
        return ResponseEntity.ok(Map.of(
            "message", "Counters reset successfully",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Log manual de estadísticas en logs del servidor.
     * 
     * POST /api/monitoring/sftp-pool/log
     */
    @PostMapping("/sftp-pool/log")
    public ResponseEntity<Map<String, Object>> logStats() {
        
        poolMonitor.logStats();
        
        return ResponseEntity.ok(Map.of(
            "message", "Stats logged to server logs",
            "checkLogs", true
        ));
    }

    // ==================== Helper Methods ====================
    
    private double calculateUtilization(CustomLazySftpSessionFactory.PoolStats stats) {
        if (stats.maxTotal() == 0) return 0.0;
        return (double) stats.active() / stats.maxTotal() * 100.0;
    }
}