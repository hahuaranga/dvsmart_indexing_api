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
package com.indra.minsait.dvsmart.indexing.application.service;

import com.indra.minsait.dvsmart.indexing.application.port.in.StartIndexFullUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 12:54:15
 * File: StartIndexFullService.java
 */

/**
 * Servicio para iniciar el job de indexación completa.
 * 
 * Incluye:
 * - Coordinación con ShedLock
 * - Validación de pre-requisitos
 * - Manejo de errores
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartIndexFullService implements StartIndexFullUseCase {

    private final JobOperator jobOperator;
    private final Job batchIndexFullJob;
    
    /**
     * Ejecuta el job de indexación con lock distribuido.
     * 
     * @SchedulerLock asegura:
     * - Solo una instancia ejecuta a la vez
     * - Lock máximo 2 horas (si el proceso cuelga, se libera)
     * - Lock mínimo 30 min (evita re-ejecuciones accidentales)
     * - Nombre único: "indexing-full-job" (compartido entre pods)
     */
    @Override
    @SchedulerLock(
        name = "indexing-full-job",
        lockAtMostFor = "PT2H",    // 2 horas máximo
        lockAtLeastFor = "PT30M"   // 30 minutos mínimo
    )
    public Long execute() {
        
        log.info("========================================");
        log.info("Starting FULL INDEXING JOB with ShedLock");
        log.info("========================================");
        
        try {
            // Validaciones pre-ejecución
            validatePrerequisites();
            
            // Parámetros del job (timestamp para hacerlo único)
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("mode", "FULL")
                    .toJobParameters();
            
            // Lanzar job
            JobExecution jobExecution = jobOperator.start(batchIndexFullJob, jobParameters);
            
            log.info("Job launched successfully");
            log.info("JobExecutionId: {}", jobExecution.getId());
            log.info("Status: {}", jobExecution.getStatus());
            log.info("========================================");
            
            return jobExecution.getId();
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("FAILED TO LAUNCH INDEXING JOB", e);
            log.error("========================================");
            throw new RuntimeException("Failed to start indexing job: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validaciones antes de ejecutar el job.
     * Evita iniciar si hay problemas conocidos.
     */
    private void validatePrerequisites() {
        log.debug("Validating job prerequisites...");
        
        // TODO: Agregar validaciones específicas si son necesarias
        // Ejemplos:
        // - Verificar conectividad MongoDB
        // - Verificar conectividad SFTP (opcional, el pool lazy lo maneja)
        // - Verificar espacio en disco
        // - Verificar que no hay otro job corriendo (Batch lo maneja)
        
        log.debug("Prerequisites validation passed");
    }
}