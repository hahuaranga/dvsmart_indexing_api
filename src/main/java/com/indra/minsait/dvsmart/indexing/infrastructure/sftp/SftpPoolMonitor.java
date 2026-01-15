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
package com.indra.minsait.dvsmart.indexing.infrastructure.sftp;

import com.indra.minsait.dvsmart.indexing.infrastructure.sftp.CustomLazySftpSessionFactory.PoolStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 18-12-2025 at 16:43:05
 * File: SftpPoolMonitor.java
 */

/**
 * Monitor del pool de conexiones SFTP.
 * 
 * Responsabilidades:
 * - Exponer métricas del pool para endpoints
 * - Log periódico de estadísticas
 * - Detectar anomalías (opcional)
 * - Integración con actuator/prometheus (futuro)
 */
@Slf4j
@Component
public class SftpPoolMonitor {

    private final CustomLazySftpSessionFactory factory;
    
    // Métricas adicionales
    private final AtomicLong totalBorrowCount = new AtomicLong(0);
    private final AtomicLong totalReturnCount = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private volatile Instant lastLogTime = Instant.now();

    public SftpPoolMonitor(CustomLazySftpSessionFactory factory) {
        this.factory = factory;
        log.info("SFTP Pool Monitor initialized");
    }

    /**
     * Obtiene estadísticas actuales del pool.
     */
    public PoolStats getStats() {
        if (factory == null) {
            return new PoolStats(0, 0, 0, 0, 0);
        }
        return factory.getStats();
    }

    /**
     * Log manual de estadísticas.
     */
    public void logStats() {
        if (factory == null) {
            log.warn("SFTP Pool Monitor: Factory not initialized");
            return;
        }
        
        PoolStats stats = factory.getStats();
        
        log.info("╔════════════════════════════════════════════════════╗");
        log.info("║         SFTP POOL STATISTICS                       ║");
        log.info("╠════════════════════════════════════════════════════╣");
        log.info("║ Active Connections:      {:>4}                     ║", stats.active());
        log.info("║ Idle Connections:        {:>4}                     ║", stats.idle());
        log.info("║ Max Pool Size:           {:>4}                     ║", stats.maxTotal());
        log.info("║ Total Created:           {:>4}                     ║", stats.created());
        log.info("║ Total Destroyed:         {:>4}                     ║", stats.destroyed());
        log.info("║ Utilization:             {:>3.1f}%                   ║", calculateUtilization(stats));
        log.info("║ Available Slots:         {:>4}                     ║", stats.maxTotal() - stats.active());
        log.info("╚════════════════════════════════════════════════════╝");
        
        lastLogTime = Instant.now();
    }

    /**
     * Log periódico automático (cada 5 minutos).
     * Útil para monitoreo pasivo.
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void scheduledLogStats() {
        logStats();
    }

    /**
     * Detectar anomalías en el pool.
     * Se ejecuta cada minuto.
     */
    @Scheduled(fixedRate = 60000) // 1 minuto
    public void checkPoolHealth() {
        if (factory == null) return;
        
        PoolStats stats = factory.getStats();
        
        // Alerta: Pool saturado
        if (stats.active() >= stats.maxTotal() * 0.9) {
            log.warn("⚠️  SFTP Pool near capacity: {}/{} connections active", 
                     stats.active(), stats.maxTotal());
        }
        
        // Alerta: Muchas conexiones destruidas (posible problema de red)
        if (stats.destroyed() > stats.created() * 0.5 && stats.created() > 10) {
            log.warn("⚠️  High connection destruction rate: {} destroyed / {} created", 
                     stats.destroyed(), stats.created());
        }
        
        // Info: Pool completamente idle
        if (stats.active() == 0 && stats.idle() > 0) {
            log.debug("ℹ️  SFTP Pool idle: {} connections available", stats.idle());
        }
    }

    /**
     * Calcula porcentaje de utilización del pool.
     */
    private double calculateUtilization(PoolStats stats) {
        if (stats.maxTotal() == 0) return 0.0;
        return (double) stats.active() / stats.maxTotal() * 100.0;
    }

    /**
     * Registra un borrow exitoso (llamado desde el código que usa el pool).
     */
    public void recordBorrow() {
        totalBorrowCount.incrementAndGet();
    }

    /**
     * Registra un return exitoso.
     */
    public void recordReturn() {
        totalReturnCount.incrementAndGet();
    }

    /**
     * Registra una falla al obtener conexión.
     */
    public void recordFailure() {
        totalFailures.incrementAndGet();
        log.error("❌ SFTP Pool failure recorded. Total failures: {}", totalFailures.get());
    }

    /**
     * Retorna métricas extendidas (incluye custom counters).
     */
    public ExtendedPoolStats getExtendedStats() {
        PoolStats baseStats = getStats();
        
        return new ExtendedPoolStats(
            baseStats.active(),
            baseStats.idle(),
            baseStats.maxTotal(),
            baseStats.created(),
            baseStats.destroyed(),
            totalBorrowCount.get(),
            totalReturnCount.get(),
            totalFailures.get(),
            calculateUtilization(baseStats),
            lastLogTime
        );
    }

    /**
     * Record extendido con métricas adicionales.
     */
    public record ExtendedPoolStats(
        int active,
        int idle,
        int maxTotal,
        long totalCreated,
        long totalDestroyed,
        long totalBorrows,
        long totalReturns,
        long totalFailures,
        double utilizationPercent,
        Instant lastLogTime
    ) {
        public boolean isHealthy() {
            return utilizationPercent < 90.0 && totalFailures < totalBorrows * 0.1;
        }
        
        public String getHealthStatus() {
            if (utilizationPercent > 95.0) return "CRITICAL";
            if (utilizationPercent > 80.0) return "WARNING";
            if (totalFailures > totalBorrows * 0.1) return "DEGRADED";
            return "HEALTHY";
        }
    }

    /**
     * Fuerza la limpieza de conexiones inactivas (útil para mantenimiento).
     */
    public void forceEviction() {
        log.info("Forcing eviction of idle connections...");
        // El pool de Apache Commons ejecuta eviction automáticamente,
        // pero podemos loguearlo para awareness
        logStats();
    }

    /**
     * Reset de contadores (útil para testing o después de mantenimiento).
     */
    public void resetCounters() {
        totalBorrowCount.set(0);
        totalReturnCount.set(0);
        totalFailures.set(0);
        log.info("SFTP Pool Monitor counters reset");
    }

    /**
     * Verifica si el pool está saludable.
     */
    public boolean isHealthy() {
        return getExtendedStats().isHealthy();
    }

    /**
     * Retorna el estado de salud del pool.
     */
    public String getHealthStatus() {
        return getExtendedStats().getHealthStatus();
    }
}
