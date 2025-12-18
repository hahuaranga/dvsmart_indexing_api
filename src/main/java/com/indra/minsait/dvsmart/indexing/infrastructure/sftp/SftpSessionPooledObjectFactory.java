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
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 18-12-2025 at 16:19:05
 * File: SftpSessionPooledObjectFactory.java
 */
	
/**
 * Factory que gestiona el ciclo de vida de las sesiones SFTP en el pool.
 * 
 * Responsabilidades:
 * - Crear nuevas sesiones bajo demanda
 * - Validar salud de sesiones existentes
 * - Destruir sesiones inválidas o cerradas
 */
@Slf4j
public class SftpSessionPooledObjectFactory extends BasePooledObjectFactory<Session<SftpClient.DirEntry>> {

    private final SessionFactory<SftpClient.DirEntry> targetFactory;

    public SftpSessionPooledObjectFactory(SessionFactory<SftpClient.DirEntry> targetFactory) {
        this.targetFactory = targetFactory;
    }

    /**
     * Crea una nueva sesión SFTP.
     * Llamado por el pool cuando necesita una nueva conexión.
     */
    @Override
    public Session<SftpClient.DirEntry> create() throws Exception {
        log.debug("Creating new SFTP session...");
        
        try {
            Session<SftpClient.DirEntry> session = targetFactory.getSession();
            
            // Verificar que la sesión está realmente abierta
            if (!session.isOpen()) {
                throw new IllegalStateException("Created session is not open");
            }
            
            log.debug("New SFTP session created successfully: {}", session.getHostPort());
            return session;
            
        } catch (Exception e) {
            log.error("Failed to create SFTP session", e);
            throw e;
        }
    }

    /**
     * Envuelve la sesión en un PooledObject.
     */
    @Override
    public PooledObject<Session<SftpClient.DirEntry>> wrap(Session<SftpClient.DirEntry> session) {
        return new DefaultPooledObject<>(session);
    }

    /**
     * Valida que una sesión sigue siendo válida antes de prestarla.
     * Crucial para detectar conexiones muertas.
     */
    @Override
    public boolean validateObject(PooledObject<Session<SftpClient.DirEntry>> p) {
        Session<SftpClient.DirEntry> session = p.getObject();
        
        try {
            // Verificación básica: está abierta
            if (!session.isOpen()) {
                log.debug("Session validation failed: not open");
                return false;
            }
            
            // Verificación avanzada: hacer un comando simple (pwd o ls /)
            // Esto detecta conexiones "zombie" que parecen abiertas pero están muertas
            session.list("/");
            
            log.trace("Session validation passed: {}", session.getHostPort());
            return true;
            
        } catch (Exception e) {
            log.warn("Session validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Destruye una sesión cuando ya no es válida o el pool la descarta.
     */
    @Override
    public void destroyObject(PooledObject<Session<SftpClient.DirEntry>> p) throws Exception {
        Session<SftpClient.DirEntry> session = p.getObject();
        
        try {
            if (session != null && session.isOpen()) {
                log.debug("Destroying SFTP session: {}", session.getHostPort());
                session.close();
            }
        } catch (Exception e) {
            log.warn("Error destroying SFTP session", e);
        }
    }

    /**
     * Llamado cuando una sesión se devuelve al pool.
     * Aquí se puede hacer limpieza si es necesario.
     */
    @Override
    public void passivateObject(PooledObject<Session<SftpClient.DirEntry>> p) throws Exception {
        // No hacer nada especial, la sesión puede reutilizarse directamente
        log.trace("Session returned to pool (idle)");
    }

    /**
     * Llamado cuando una sesión se saca del pool para uso.
     */
    @Override
    public void activateObject(PooledObject<Session<SftpClient.DirEntry>> p) throws Exception {
        // Verificar que sigue abierta
        Session<SftpClient.DirEntry> session = p.getObject();
        
        if (!session.isOpen()) {
            throw new IllegalStateException("Session is not open");
        }
        
        log.trace("Session activated from pool");
    }
}
