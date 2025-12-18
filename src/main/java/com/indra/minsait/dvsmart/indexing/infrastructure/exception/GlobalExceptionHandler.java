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
package com.indra.minsait.dvsmart.indexing.infrastructure.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 12-12-2025 at 13:26:24
 * File: GlobalExceptionHandler.java
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred", ex);
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "error", "Internal Server Error",
                    "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Illegal argument exception occurred", ex);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", "Bad Request",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Invalid request parameters"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "error", "Internal Server Error",
                    "message", "An unexpected error occurred"
                ));
    }
    
    /**
     * Maneja el caso cuando se intenta ejecutar un job que ya está corriendo.
     * Retorna HTTP 409 CONFLICT con mensaje descriptivo.
     */
    @ExceptionHandler(JobAlreadyRunningException.class)
    public ResponseEntity<Map<String, Object>> handleJobAlreadyRunning(JobAlreadyRunningException ex) {
        log.warn("Job execution conflict: {}", ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", HttpStatus.CONFLICT.value(),
                    "error", "Conflict",
                    "message", ex.getMessage(),
                    "detail", "Please wait for the current job to complete before starting a new one"
                ));
    }    
}
