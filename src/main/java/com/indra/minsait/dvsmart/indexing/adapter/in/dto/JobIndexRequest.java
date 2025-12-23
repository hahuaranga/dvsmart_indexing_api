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
package com.indra.minsait.dvsmart.indexing.adapter.in.dto;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 23-12-2025 at 13:38:53
 * File: JobIndexRequest.java
 */

public record JobIndexRequest(
		@NotBlank(message = "Job name is required")
	    String jobName,
	    Map<String, Object> parameters
) {
    public JobIndexRequest {
        // Inicializar parámetros vacíos si es null
        if (parameters == null) {
            parameters = new HashMap<>();
        }
    }
}