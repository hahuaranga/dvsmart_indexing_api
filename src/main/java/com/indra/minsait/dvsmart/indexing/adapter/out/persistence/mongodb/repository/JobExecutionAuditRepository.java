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
package com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.repository;

import com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity.JobExecutionAuditDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 26-12-2025 at 12:16:16
 * File: JobExecutionAuditRepository.java
 */

/**
 * Repositorio para auditoría de ejecuciones de jobs.
 */
@Repository
public interface JobExecutionAuditRepository extends MongoRepository<JobExecutionAuditDocument, String> {
//    
//    Optional<JobExecutionAuditDocument> findByAuditId(String auditId);
//    
    Optional<JobExecutionAuditDocument> findByJobExecutionId(Long jobExecutionId);
//    
//    List<JobExecutionAuditDocument> findByJobNameOrderByStartTimeDesc(String jobName);
//    
//    List<JobExecutionAuditDocument> findByStatusOrderByStartTimeDesc(String status);
//    
//    List<JobExecutionAuditDocument> findByJobNameAndStatusOrderByStartTimeDesc(String jobName, String status);
//    
//    List<JobExecutionAuditDocument> findByStartTimeBetweenOrderByStartTimeDesc(Instant start, Instant end);
//    
//    long countByJobNameAndStatus(String jobName, String status);
}
