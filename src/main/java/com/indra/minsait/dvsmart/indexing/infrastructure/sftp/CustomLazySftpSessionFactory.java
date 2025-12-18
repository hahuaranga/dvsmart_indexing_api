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
package com.indra.minsait.dvsmart.indexing.infrastructure.sftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import java.io.IOException;
import java.time.Duration;


/**
 * Author: hahuaranga@indracompany.com
 * Created on: 18-12-2025 at 16:17:20
 * File: CustomLazySftpSessionFactory.java
 */

/**
 * SessionFactory personalizado con pool lazy y validación de conexiones.
 * 
 * Características:
 * - Pool lazy: Conexiones creadas bajo demanda
 * - Validación pre-uso: Verifica salud antes de retornar
 * - Eviction: Cierra conexiones inactivas automáticamente
 * - Thread-safe: Seguro para uso concurrente
 */
@Slf4j
public class CustomLazySftpSessionFactory implements SessionFactory<SftpClient.DirEntry> {

    private final GenericObjectPool<Session<SftpClient.DirEntry>> pool;
    private final SftpSessionPooledObjectFactory pooledFactory;

    public CustomLazySftpSessionFactory(
            SessionFactory<SftpClient.DirEntry> targetFactory,
            int maxPoolSize,
            int initialSize,
            long maxWaitMillis,
            boolean testOnBorrow,
            long timeBetweenEvictionRunsMillis,
            long minEvictableIdleTimeMillis) {

        log.info("Initializing Lazy SFTP Session Pool: maxSize={}, initialSize={}, lazy={}",
                maxPoolSize, initialSize, initialSize == 0);

        // Factory que crea sesiones bajo demanda
        this.pooledFactory = new SftpSessionPooledObjectFactory(targetFactory);

        // Configuración del pool
        GenericObjectPoolConfig<Session<SftpClient.DirEntry>> config = new GenericObjectPoolConfig<>();
        
        // Tamaño del pool
        config.setMaxTotal(maxPoolSize);
        config.setMaxIdle(maxPoolSize);
        config.setMinIdle(0); // No mantener sesiones mínimas
        
        // Comportamiento LIFO (Last In First Out) para reusar conexiones recientes
        config.setLifo(true);
        
        // Timeouts
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        
        // Validación
        config.setTestOnBorrow(testOnBorrow);
        config.setTestWhileIdle(true);
        config.setTestOnReturn(false);
        
        // Eviction (limpieza de inactivas)
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
        config.setMinEvictableIdleTime(Duration.ofMillis(minEvictableIdleTimeMillis));
        config.setNumTestsPerEvictionRun(3);
        
        // Crear pool
        this.pool = new GenericObjectPool<>(pooledFactory, config);
        
        // Pre-crear sesiones iniciales si se especifica
        if (initialSize > 0) {
            log.info("Pre-creating {} initial sessions...", initialSize);
            for (int i = 0; i < initialSize; i++) {
                try {
                    pool.addObject();
                } catch (Exception e) {
                    log.warn("Failed to pre-create session {}", i, e);
                }
            }
        }
        
        log.info("SFTP Session Pool initialized successfully");
    }

    @Override
    public Session<SftpClient.DirEntry> getSession() {
        try {
            log.debug("Borrowing session from pool (active={}, idle={})",
                    pool.getNumActive(), pool.getNumIdle());
            
            Session<SftpClient.DirEntry> session = pool.borrowObject();
            
            log.debug("Session borrowed successfully (active={}, idle={})",
                    pool.getNumActive(), pool.getNumIdle());
            
            return new PooledSftpSession(session, pool);
            
        } catch (Exception e) {
            log.error("Failed to borrow session from pool", e);
            throw new RuntimeException("Could not obtain SFTP session", e);
        }
    }

    /**
     * Wrapper que devuelve la sesión al pool al cerrarse.
     */
    private static class PooledSftpSession implements Session<SftpClient.DirEntry> {
        
        private final Session<SftpClient.DirEntry> delegate;
        private final GenericObjectPool<Session<SftpClient.DirEntry>> pool;
        private volatile boolean closed = false;

        public PooledSftpSession(
                Session<SftpClient.DirEntry> delegate,
                GenericObjectPool<Session<SftpClient.DirEntry>> pool) {
            this.delegate = delegate;
            this.pool = pool;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    log.debug("Returning session to pool");
                    pool.returnObject(delegate);
                } catch (Exception e) {
                    log.warn("Error returning session to pool", e);
                }
            }
        }

        @Override
        public boolean remove(String path) throws IOException {
            return delegate.remove(path);
        }

        @Override
        public SftpClient.DirEntry[] list(String path) throws IOException {
            return delegate.list(path);
        }

        @Override
        public void read(String source, java.io.OutputStream outputStream) throws IOException {
            delegate.read(source, outputStream);
        }

        @Override
        public void write(java.io.InputStream inputStream, String destination) throws IOException {
            delegate.write(inputStream, destination);
        }

        @Override
        public void append(java.io.InputStream inputStream, String destination) throws IOException {
            delegate.append(inputStream, destination);
        }

        @Override
        public boolean mkdir(String directory) throws IOException {
            return delegate.mkdir(directory);
        }

        @Override
        public boolean rmdir(String directory) throws IOException {
            return delegate.rmdir(directory);
        }

        @Override
        public void rename(String pathFrom, String pathTo) throws IOException {
            delegate.rename(pathFrom, pathTo);
        }

        @Override
        public boolean isOpen() {
            return !closed && delegate.isOpen();
        }

        @Override
        public boolean exists(String path) throws IOException {
            return delegate.exists(path);
        }

        @Override
        public String[] listNames(String path) throws IOException {
            return delegate.listNames(path);
        }

        @Override
        public java.io.InputStream readRaw(String source) throws IOException {
            return delegate.readRaw(source);
        }

        @Override
        public boolean finalizeRaw() throws IOException {
            return delegate.finalizeRaw();
        }

        @Override
        public Object getClientInstance() {
            return delegate.getClientInstance();
        }

        @Override
        public String getHostPort() {
            return delegate.getHostPort();
        }
    }

    /**
     * Retorna estadísticas del pool (útil para monitoring).
     */
    public PoolStats getStats() {
        return new PoolStats(
            pool.getNumActive(),
            pool.getNumIdle(),
            pool.getMaxTotal(),
            pool.getCreatedCount(),
            pool.getDestroyedCount()
        );
    }

    public record PoolStats(
        int active,
        int idle,
        int maxTotal,
        long created,
        long destroyed
    ) {}

    /**
     * Cierra el pool y todas sus conexiones.
     */
    public void destroy() {
        log.info("Closing SFTP session pool...");
        pool.close();
        log.info("SFTP session pool closed");
    }
}
