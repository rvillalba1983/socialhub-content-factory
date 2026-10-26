package com.socialhub.publishing.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Ciclo de vida de una publicación, con las transiciones permitidas. */
public enum PublicationStatus {
    IDEA("idea", "Idea"),
    PROPOSAL("proposal", "Propuesta"),
    IN_PRODUCTION("production", "En producción"),
    DRAFT("draft", "Borrador"),
    IN_REVIEW("review", "Pendiente de revisión"),
    APPROVED("approved", "Aprobada"),
    SCHEDULED("scheduled", "Programada"),
    PUBLISHING("publishing", "Publicando"),
    PUBLISHED("published", "Publicada"),
    PUBLISH_ERROR("error", "Error de publicación"),
    CANCELLED("cancelled", "Cancelada");

    private final String code;
    private final String label;

    PublicationStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    private static final Map<PublicationStatus, Set<PublicationStatus>> NEXT = Map.ofEntries(
            Map.entry(IDEA, EnumSet.of(PROPOSAL, CANCELLED)),
            Map.entry(PROPOSAL, EnumSet.of(IN_PRODUCTION, IDEA, CANCELLED)),
            Map.entry(IN_PRODUCTION, EnumSet.of(DRAFT, CANCELLED)),
            Map.entry(DRAFT, EnumSet.of(IN_REVIEW, IN_PRODUCTION, CANCELLED)),
            Map.entry(IN_REVIEW, EnumSet.of(APPROVED, DRAFT, CANCELLED)),
            Map.entry(APPROVED, EnumSet.of(SCHEDULED, IN_REVIEW, CANCELLED)),
            Map.entry(SCHEDULED, EnumSet.of(PUBLISHING, APPROVED, CANCELLED)),
            Map.entry(PUBLISHING, EnumSet.of(PUBLISHED, PUBLISH_ERROR)),
            Map.entry(PUBLISH_ERROR, EnumSet.of(SCHEDULED, APPROVED, CANCELLED)),
            Map.entry(PUBLISHED, EnumSet.noneOf(PublicationStatus.class)),
            Map.entry(CANCELLED, EnumSet.of(IDEA)));

    public boolean canMoveTo(PublicationStatus next) {
        return NEXT.get(this).contains(next);
    }

    @JsonValue
    public String code() { return code; }

    public String label() { return label; }

    public static PublicationStatus fromCode(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de publicación desconocido: " + code));
    }
}
