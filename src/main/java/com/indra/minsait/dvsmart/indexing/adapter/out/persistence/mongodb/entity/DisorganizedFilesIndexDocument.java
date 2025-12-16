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
package com.indra.minsait.dvsmart.indexing.adapter.out.persistence.mongodb.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 14:51:33
 * File: DisorganizedFilesIndexDocument.java
 */

/**
 * Documento MongoDB para colección disorganized-files-index.
 * Representa un archivo indexado desde SFTP.
 */
@Data
@Builder
@Document(collection = "disorganized-files-index")
public class DisorganizedFilesIndexDocument {
    
    @Id
    private String id;                // MongoDB _id (auto-generado)
    
    @Indexed(unique = true)
    private String idUnico;           // SHA-256 del path completo
    
    private String rutaOrigen;        // Path completo en SFTP
    private String nombre;            // Nombre del archivo
    private Instant mtime;            // Fecha modificación
    private Long tamanio;             // Tamaño en bytes
    private String extension;         // Extensión
    private Instant indexadoEn;       // Timestamp indexación
}
