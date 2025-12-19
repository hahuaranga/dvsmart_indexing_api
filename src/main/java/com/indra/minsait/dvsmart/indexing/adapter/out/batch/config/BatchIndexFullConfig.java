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
package com.indra.minsait.dvsmart.indexing.adapter.out.batch.config;

import com.indra.minsait.dvsmart.indexing.adapter.out.batch.processor.MetadataExtractorProcessor;
import com.indra.minsait.dvsmart.indexing.adapter.out.batch.reader.DirectoryQueueItemReader;
import com.indra.minsait.dvsmart.indexing.adapter.out.batch.writer.BulkUpsertMongoItemWriter;
import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import com.indra.minsait.dvsmart.indexing.domain.service.DirectoryDiscoveryService;
import com.indra.minsait.dvsmart.indexing.infrastructure.config.BatchConfigProperties;
import com.indra.minsait.dvsmart.indexing.infrastructure.config.SftpConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.Queue;
import java.util.concurrent.Future;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 13:23:54
 * File: BatchReorgFullConfig.java
 */

/**
 * Configuración del Job de Indexación Completa.
 * 
 * Flujo:
 * 1. Pre-procesamiento: Descubrir todos los directorios (una sola sesión SFTP)
 * 2. Reader: Lee archivos directorio por directorio (usa pool lazy)
 * 3. Processor: Extrae metadata de cada archivo (paralelo, sin SFTP)
 * 4. Writer: Bulk upsert a MongoDB (paralelo, sin SFTP)
 * 
 * Ventajas del pool lazy:
 * - Reader usa pocas conexiones (1-2 típicamente)
 * - Conexiones se liberan automáticamente al terminar
 * - No hay conexiones idle consumiendo recursos
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchIndexFullConfig {

    private final JobRepository jobRepository;
    private final DirectoryDiscoveryService directoryDiscoveryService;
    private final BulkUpsertMongoItemWriter bulkWriter;
    private final BatchConfigProperties batchProps;
    private final SftpConfigProperties sftpProps;
    private final MetadataExtractorProcessor metadataExtractorProcessor;
    
    @Qualifier("sftpOriginTemplate")
    private final SftpRemoteFileTemplate sftpTemplate;

    @Bean
    JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    /**
     * TaskExecutor para procesamiento asíncrono.
     * Solo para processor y writer (sin SFTP).
     */
    @Bean(name = "indexingTaskExecutor")
    TaskExecutor indexingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProps.getThreadPoolSize());
        executor.setMaxPoolSize(batchProps.getThreadPoolSize());
        executor.setQueueCapacity(batchProps.getQueueCapacity());
        executor.setThreadNamePrefix("batch-index-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Pre-procesamiento: Descubrir todos los directorios.
     * 
     * Ventajas con pool lazy:
     * - Usa 1 sola sesión SFTP para todo el descubrimiento
     * - Template adquiere y libera sesión automáticamente
     * - Sesión se devuelve al pool al terminar
     */
    private Queue<String> discoverDirectoriesBeforeJob() {
        log.info("========================================");
        log.info("Starting directory discovery phase...");
        log.info("========================================");
        
        long start = System.currentTimeMillis();
        
        Queue<String> directories = directoryDiscoveryService.discoverDirectories(
            sftpTemplate,
            sftpProps.getOrigin().getBaseDir()
        );
        
        long duration = System.currentTimeMillis() - start;
        
        log.info("========================================");
        log.info("Directory discovery completed");
        log.info("Total directories: {}", directories.size());
        log.info("Duration: {} ms", duration);
        log.info("========================================");
        
        return directories;
    }

    /**
     * Reader que consume la queue de directorios.
     * 
     * Usa pool lazy:
     * - Adquiere sesión solo cuando lee archivos de un directorio
     * - Libera sesión al terminar cada directorio
     * - Típicamente usa 1-2 conexiones concurrentes
     */
    @Bean
    ItemReader<SftpFileEntry> directoryQueueReader() {
        Queue<String> directoryQueue = discoverDirectoriesBeforeJob();
        //return new DirectoryQueueItemReader(sftpTemplate, directoryQueue);
        return new DirectoryQueueItemReader(sftpTemplate, directoryDiscoveryService, directoryQueue.element());
    }

    /**
     * Processor asíncrono para extracción de metadata.
     * No usa SFTP, solo procesa datos en memoria.
     */
    @Bean
    AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncMetadataProcessor() {
        AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncProcessor = 
            new AsyncItemProcessor<>(metadataExtractorProcessor);
        asyncProcessor.setTaskExecutor(indexingTaskExecutor());
        return asyncProcessor;
    }

    /**
     * Writer asíncrono para MongoDB.
     * No usa SFTP, solo escribe a MongoDB.
     */
    @Bean
    AsyncItemWriter<ArchivoMetadata> asyncBulkWriter() {
        return new AsyncItemWriter<>(bulkWriter);
    }

    /**
     * Step principal de indexación.
     * 
     * Chunk size: 500 (balance entre throughput y memoria)
     * - Reader: Secuencial, 1-2 conexiones SFTP
     * - Processor: Paralelo, sin SFTP
     * - Writer: Paralelo, sin SFTP
     */
    @Bean
    Step indexingStep() {
        return new StepBuilder("indexingStep", jobRepository)
                .<SftpFileEntry, Future<ArchivoMetadata>>chunk(500)
                .reader(directoryQueueReader())
                .processor(asyncMetadataProcessor())
                .writer(asyncBulkWriter())
                .build();
    }

    /**
     * Job de indexación completa.
     * Coordinado con ShedLock para evitar ejecuciones concurrentes.
     */
    @Bean(name = "batchIndexFullJob")
    Job batchIndexFullJob() {
        return new JobBuilder("BATCH-INDEX-FULL", jobRepository)
                .start(indexingStep())
                .build();
    }
}