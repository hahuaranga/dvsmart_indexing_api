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
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.Map;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class StartIndexFullService implements StartIndexFullUseCase {

	private final JobOperator jobOperator;
	
	private final Job batchIndexFullJob;
    
    @Override
    public Long execute(@NotBlank(message = "Job name is required") String string, Map<String, Object> map) {
        log.info("Starting FULL INDEXING JOB");
        
        try {
            validatePrerequisites();
            
            JobParametersBuilder paramsBuilder = new JobParametersBuilder();
            paramsBuilder.addLocalDateTime("timestamp", LocalDateTime.now(), true);
            map.forEach((key, value) -> {
                if (value instanceof String) {
                	paramsBuilder.addString(key, (String) value);
                } else if (value instanceof Long) {
                	paramsBuilder.addLong(key, (Long) value);
                } else if (value instanceof Double) {
                	paramsBuilder.addDouble(key, (Double) value);
                }
            });        
            JobParameters jobParameters = paramsBuilder.toJobParameters();
            log.info("Generated parameters: {}", jobParameters);

            JobExecution jobExecution = jobOperator.run(batchIndexFullJob, jobParameters);
            
            log.info("Job launched: executionId={}, status={}", 
                jobExecution.getId(), 
                jobExecution.getStatus());
            
            return jobExecution.getId();
            
        } catch (Exception e) {
            log.error("Failed to start job", e);
            throw new RuntimeException("Failed to start job: " + e.getMessage(), e);
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