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
 * File: StartReorganizeFullService.java
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class StartIndexFullService implements StartIndexFullUseCase {

    private final JobOperator jobOperator;

    private final Job batchIndexFullJob;
    
    @Override
    @SchedulerLock(
            name = "batchIndexFullJob", // Nombre único del lock
            lockAtMostFor = "4h",       // Máximo 4 horas si crashea
            lockAtLeastFor = "1s"       // Mínimo 1 segundo
        )
    public Long execute() {
        // ShedLock automáticamente adquiere el lock aquí
        // Si otro proceso ya tiene el lock, lanza UnableToAcquireLockException
        try {           
            // ✅ JobParametersBuilder en lugar de Properties
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("uniqueId", java.util.UUID.randomUUID().toString())
                    .toJobParameters();        	
        	
            // Lanzar job usando el nombre del job (definido en BatchIndexFullConfig)
            JobExecution jobExecution = jobOperator.start(batchIndexFullJob, jobParameters);
            
            log.info("Job launched successfully. JobExecutionId: {}, Status: {}", 
                    jobExecution.getId(), jobExecution.getStatus());
            
            return jobExecution.getId();
            
        } catch (Exception e) {
            log.error("Failed to launch batch job", e);
            throw new RuntimeException("Failed to start reorganization job", e);
        }
        // ShedLock automáticamente libera el lock aquí cuando el método termina
    }
}