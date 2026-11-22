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
 * @param level {@code BLOCKING}: bloquea hasta corregirlo o aceptarlo; {@code INFO}: solo informa (enlaces, texto repetido)
 */
@ValueObject
public record QualityIssue(String key, Source source, String message, @Nullable String acceptedReason,
                           @Nullable Instant acceptedAt, Level level) {

    public enum Source { CODE, CLAUDE }

    public enum Level { BLOCKING, INFO }

    public QualityIssue(String key, Source source, String message, @Nullable String acceptedReason, @Nullable Instant acceptedAt) {
        this(key, source, message, acceptedReason, acceptedAt, Level.BLOCKING);
    }

    /** Un aviso informativo: nunca bloquea ni hay que aceptarlo. */
    public static QualityIssue info(String key, String message) {
        return new QualityIssue(key, Source.CODE, message, null, null, Level.INFO);
    }

    public boolean open() { return level == Level.BLOCKING && acceptedReason == null; }

    public QualityIssue accepted(String reason, Instant at) {
        return new QualityIssue(key, source, message, reason, at, level);
    }
}
