package com.socialhub.publishing.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Un aviso del control de calidad de una publicación.
 *
 * @param key identifica el aviso para poder aceptarlo (por ejemplo, {@code FORBIDDEN:compra ya})
 * @param source quién lo detectó: las comprobaciones del código o la revisión de Claude
 * @param acceptedReason motivo con el que Raúl lo aceptó, o {@code null} si sigue abierto
 */
@ValueObject
public record QualityIssue(String key, Source source, String message, @Nullable String acceptedReason,
                           @Nullable Instant acceptedAt) {

    public enum Source { CODE, CLAUDE }

    public boolean open() { return acceptedReason == null; }

    public QualityIssue accepted(String reason, Instant at) {
        return new QualityIssue(key, source, message, reason, at);
    }
}
