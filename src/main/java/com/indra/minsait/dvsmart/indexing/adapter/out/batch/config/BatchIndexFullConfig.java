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

import com.indra.minsait.dvsmart.indexing.adapter.out.batch.reader.DirectoryQueueItemReader;
import com.indra.minsait.dvsmart.indexing.adapter.out.batch.writer.BulkUpsertMongoItemWriter;
import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import com.indra.minsait.dvsmart.indexing.domain.service.DirectoryDiscoveryService;
import com.indra.minsait.dvsmart.indexing.domain.service.FileMetadataService;
import com.indra.minsait.dvsmart.indexing.infrastructure.config.BatchConfigProperties;
import com.indra.minsait.dvsmart.indexing.infrastructure.config.SftpConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchIndexFullConfig {

    private final JobRepository jobRepository;
    private final FileMetadataService metadataService;
    private final DirectoryDiscoveryService directoryDiscoveryService;
    private final BulkUpsertMongoItemWriter bulkWriter;
    private final BatchConfigProperties batchProps;
    private final SftpConfigProperties sftpProps;
    
    // ✅ Inyectar template directamente (más limpio)
    @Qualifier("sftpOriginTemplate")
    private final SftpRemoteFileTemplate sftpTemplate;

    @Bean
    JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    @Bean(name = "indexingTaskExecutor")
    TaskExecutor indexingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProps.getThreadPoolSize());
        executor.setMaxPoolSize(batchProps.getThreadPoolSize());
        executor.setQueueCapacity(batchProps.getQueueCapacity());
        executor.setThreadNamePrefix("index-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Pre-procesamiento: Descubrir todos los directorios.
     * Usa template para manejo seguro de sesiones.
     */
    private Queue<String> discoverDirectoriesBeforeJob() {
        log.info("Starting directory discovery phase...");
        
        // ✅ Template maneja la sesión automáticamente
        return directoryDiscoveryService.discoverDirectories(
            sftpTemplate,
            sftpProps.getOrigin().getBaseDir()
        );
    }

    /**
     * Reader que consume la queue de directorios.
     */
    @Bean
    ItemReader<SftpFileEntry> directoryQueueReader() {
        Queue<String> directoryQueue = discoverDirectoriesBeforeJob();
        
        // ✅ Pasar template al reader (no SessionFactory)
        return new DirectoryQueueItemReader(sftpTemplate, directoryQueue);
    }

    @Bean
    ItemProcessor<SftpFileEntry, ArchivoMetadata> metadataProcessor() {
        return entry -> {
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            return metadataService.toMetadata(entry);
        };
    }

    @Bean
    AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncMetadataProcessor() {
        AsyncItemProcessor<SftpFileEntry, ArchivoMetadata> asyncProcessor = 
            new AsyncItemProcessor<>(metadataProcessor());
        asyncProcessor.setTaskExecutor(indexingTaskExecutor());
        return asyncProcessor;
    }

    @Bean
    AsyncItemWriter<ArchivoMetadata> asyncBulkWriter() {
        return new AsyncItemWriter<>(bulkWriter);
    }

    @Bean
    Step indexingStep() {
        return new StepBuilder("indexingStep", jobRepository)
                .<SftpFileEntry, Future<ArchivoMetadata>>chunk(500)
                .reader(directoryQueueReader())
                .processor(asyncMetadataProcessor())
                .writer(asyncBulkWriter())
                .build();
    }

    @Bean(name = "batchIndexFullJob")
    Job batchIndexFullJob() {
        return new JobBuilder("BATCH-INDEX-FULL", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(indexingStep())
                .build();
    }
}