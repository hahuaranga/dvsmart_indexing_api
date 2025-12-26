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
package com.indra.minsait.dvsmart.indexing.adapter.out.batch.listener;

import com.indra.minsait.dvsmart.indexing.domain.service.JobAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 12:22:22
 * File: JobExecutionAuditListener.java
 */

/**
 * Listener para auditoría de ejecuciones de jobs.
 * 
 * Se ejecuta:
 * - beforeJob: Al inicio del job (crea registro)
 * - afterJob: Al finalizar el job (actualiza registro)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionAuditListener implements JobExecutionListener {
    
    private final JobAuditService auditService;
    
    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("════════════════════════════════════════════════════════");
        log.info("📋 AUDIT: Creating audit record for job execution");
        log.info("   Job Name: {}", jobExecution.getJobInstance().getJobName());
        log.info("   Execution ID: {}", jobExecution.getId());
        log.info("════════════════════════════════════════════════════════");
        
        auditService.createAuditRecord(jobExecution);
    }
    
    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("════════════════════════════════════════════════════════");
        log.info("📋 AUDIT: Updating audit record for job execution");
        log.info("   Job Name: {}", jobExecution.getJobInstance().getJobName());
        log.info("   Execution ID: {}", jobExecution.getId());
        log.info("   Status: {}", jobExecution.getStatus());
        log.info("   Exit Code: {}", jobExecution.getExitStatus().getExitCode());
        log.info("════════════════════════════════════════════════════════");
        
        auditService.updateAuditRecord(jobExecution);
    }
}