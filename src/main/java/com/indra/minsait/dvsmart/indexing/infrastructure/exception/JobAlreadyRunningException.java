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
package com.indra.minsait.dvsmart.indexing.infrastructure.exception;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 17-12-2025 at 22:18:46
 * File: JobAlreadyRunningException.java
 */

/**
 * Excepción lanzada cuando se intenta ejecutar un job que ya está en ejecución.
 * El GlobalExceptionHandler la mapea a HTTP 409 CONFLICT.
 */
public class JobAlreadyRunningException extends RuntimeException {
    
	private static final long serialVersionUID = 1L;

	public JobAlreadyRunningException(String message) {
        super(message);
    }
    
    public JobAlreadyRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}