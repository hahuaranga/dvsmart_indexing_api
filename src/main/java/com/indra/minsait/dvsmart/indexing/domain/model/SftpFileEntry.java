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

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 16-12-2025 at 14:35:09
 * File: SftpFileEntry.java
 */

/**
 * Representa una entrada de archivo en el listado de SFTP.
 * Modelo intermedio antes de convertir a ArchivoMetadata.
 */
@Data
@Builder
public class SftpFileEntry {
    private String fullPath;          // Path completo (/data/files/doc.pdf)
    private String filename;          // Nombre del archivo (doc.pdf)
    private long size;                // Tamaño en bytes
    private long modificationTime;    // Unix timestamp (millis)
    private boolean isDirectory;      // true si es directorio
}
