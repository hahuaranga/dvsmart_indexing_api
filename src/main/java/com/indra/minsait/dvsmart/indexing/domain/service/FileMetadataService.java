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
package com.indra.minsait.dvsmart.indexing.domain.service;

import com.indra.minsait.dvsmart.indexing.domain.model.ArchivoMetadata;
import com.indra.minsait.dvsmart.indexing.domain.model.SftpFileEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;


/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 13:56:41
 * File: FileMetadataService.java
 */

/**
 * Servicio de dominio para procesar metadata de archivos.
 * Lógica pura sin dependencias de frameworks.
 */
@Slf4j
@Service
public class FileMetadataService {

    /**
     * Convierte un SftpFileEntry a ArchivoMetadata con todos los campos calculados.
     */
    public ArchivoMetadata toMetadata(SftpFileEntry entry) {
        String extension = extractExtension(entry.getFilename());
        String idUnico = generateIdUnico(entry.getFullPath());
        
        return ArchivoMetadata.builder()
                .idUnico(idUnico)
                .rutaOrigen(entry.getFullPath())
                .nombre(entry.getFilename())
                .mtime(Instant.ofEpochMilli(entry.getModificationTime()))
                .tamanio(entry.getSize())
                .extension(extension)
                .indexadoEn(Instant.now())
                .build();
    }

    /**
     * Genera un ID único basado en el path completo usando SHA-256.
     */
    public String generateIdUnico(String fullPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fullPath.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate unique ID", e);
        }
    }

    /**
     * Extrae la extensión del archivo.
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return filename.substring(lastDot + 1).toLowerCase();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}