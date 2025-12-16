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
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.stereotype.Service;
import java.util.Properties;

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

    @Override
    public Long execute() {
        try {
            // Crear Properties con los parámetros del job
            Properties jobProperties = new Properties();
            jobProperties.setProperty("timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Lanzar job usando el nombre del job (definido en BatchIndexFullConfig)
            Long jobExecutionId = jobOperator.start("BATCH-INDEX-FULL", jobProperties);
            
            log.info("Job launched successfully. JobExecutionId: {}", jobExecutionId);
            
            return jobExecutionId;
            
        } catch (NoSuchJobException e) {
            log.error("Job not found in registry: BATCH-INDEX-FULL", e);
            throw new RuntimeException("Job 'BATCH-INDEX-FULL' not registered in JobRegistry", e);
        } catch (JobInstanceAlreadyExistsException e) {
            log.warn("Job instance already exists with these parameters", e);
            throw new RuntimeException("Duplicate job instance. Job may already be running.", e);
        } catch (Exception e) {
            log.error("Failed to launch batch job", e);
            throw new RuntimeException("Failed to start reorganization job", e);
        }
    }
}