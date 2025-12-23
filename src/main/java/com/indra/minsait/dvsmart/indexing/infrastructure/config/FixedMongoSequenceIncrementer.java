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
package com.indra.minsait.dvsmart.indexing.infrastructure.config;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.util.Assert;

/**
 * Author: hahuaranga@indracompany.com
 * Created on: 23-12-2025 at 15:52:45
 * File: FixedMongoSequenceIncrementer.java
 */

/**
 * Custom MongoDB sequence incrementer que soluciona el bug de ClassCastException.
 * 
 * BUG ORIGINAL en Spring Batch 6.0.0:
 * - MongoSequenceIncrementer hace: (Long) document.get("value")
 * - Falla cuando MongoDB retorna Integer en lugar de Long
 * 
 * NUESTRA SOLUCIÓN:
 * - Usamos: ((Number) document.get("value")).longValue()
 * - Funciona con Integer, Long, y cualquier Number
 */
public class FixedMongoSequenceIncrementer implements DataFieldMaxValueIncrementer {

    private final MongoOperations mongoOperations;
    private final String collectionName;
    private final String incrementerName;

    /**
     * Constructor.
     * 
     * @param mongoOperations MongoTemplate configurado
     * @param collectionName Colección de secuencias (usualmente "BATCH_SEQUENCES")
     * @param incrementerName Nombre de la secuencia específica
     */
    public FixedMongoSequenceIncrementer(
            MongoOperations mongoOperations,
            String collectionName,
            String incrementerName) {
        
        Assert.notNull(mongoOperations, "MongoOperations must not be null");
        Assert.hasText(collectionName, "Collection name must not be empty");
        Assert.hasText(incrementerName, "Incrementer name must not be empty");
        
        this.mongoOperations = mongoOperations;
        this.collectionName = collectionName;
        this.incrementerName = incrementerName;
    }

    @Override
    public int nextIntValue() {
        return (int) nextLongValue();
    }

    @Override
    public long nextLongValue() {
        
        Query query = Query.query(Criteria.where("_id").is(incrementerName));
        
        // Incrementar en 1
        Update update = new Update().inc("value", 1L);
        
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .returnNew(true)   // Retornar valor DESPUÉS del incremento
                .upsert(true);     // Crear si no existe (con value=1)
        
        Document document = mongoOperations.findAndModify(
            query,
            update,
            options,
            Document.class,
            collectionName
        );
        
        if (document == null) {
            throw new IllegalStateException(
                "Unable to increment sequence '" + incrementerName + "' in collection '" + collectionName + "'"
            );
        }
        
        Object value = document.get("value");
        
        if (value == null) {
            throw new IllegalStateException(
                "Sequence '" + incrementerName + "' has null value in collection '" + collectionName + "'"
            );
        }
        
        // ✅ CRÍTICO: Manejar tanto Integer como Long
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        throw new IllegalStateException(
            "Sequence '" + incrementerName + "' has non-numeric value: " + 
            value.getClass().getName() + " in collection '" + collectionName + "'"
        );
    }

    @Override
    public String nextStringValue() {
        return String.valueOf(nextLongValue());
    }
}
