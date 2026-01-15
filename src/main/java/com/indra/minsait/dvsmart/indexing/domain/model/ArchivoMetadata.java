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
    
    // Identificación
    private String idUnico;
    
    // Metadata del archivo
    private String sourcePath;           // ✅ CAMBIO: antes rutaOrigen
    private String fileName;             // ✅ CAMBIO: antes nombre
    private String extension;
    private Long fileSize;               // ✅ CAMBIO: antes tamanio
    private Instant lastModificationDate; // ✅ CAMBIO: antes mtime
    
    // Control de indexación
    private String indexing_status;      // ✅ NUEVO
    private Instant indexing_indexedAt;  // ✅ NUEVO
    private String indexing_errorDescription;  // ✅ CAMBIO: Agregar campo
}
