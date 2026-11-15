package com.socialhub.ai.domain;

import java.util.UUID;
import org.jmolecules.ddd.types.Identifier;

/** Identificador de un envío a Claude (el {@code idempotencyKey} del encargo). */
public record AiRequestId(String value) implements Identifier {

    public AiRequestId {
        if (value.isBlank()) throw new IllegalArgumentException("El identificador del envío no puede estar vacío");
    }

    public static AiRequestId random() { return new AiRequestId(UUID.randomUUID().toString()); }

    @Override
    public String toString() { return value; }
}
