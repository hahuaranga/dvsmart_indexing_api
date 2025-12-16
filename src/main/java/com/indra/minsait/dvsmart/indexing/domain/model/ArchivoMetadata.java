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
package com.indra.minsait.dvsmart.indexing.domain.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Modelo de dominio que representa la metadata de un archivo indexado.
 */
@Data
@Builder
public class ArchivoMetadata {
    private String idUnico;           // Hash del path completo
    private String rutaOrigen;        // Path completo en SFTP
    private String nombre;            // Nombre del archivo
    private Instant mtime;            // Fecha de modificación
    private Long tamanio;             // Tamaño en bytes
    private String extension;         // Extensión del archivo
    private Instant indexadoEn;       // Timestamp de indexación
}
