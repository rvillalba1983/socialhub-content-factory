package com.socialhub.publishing.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Ciclo de vida de una publicación, con las transiciones permitidas. */
public enum PublicationStatus {
    IDEA("Idea"),
    PROPOSAL("Propuesta"),
    IN_PRODUCTION("En producción"),
    DRAFT("Borrador"),
    IN_REVIEW("Pendiente de revisión"),
    APPROVED("Aprobada"),
    SCHEDULED("Programada"),
    PUBLISHING("Publicando"),
    PUBLISHED("Publicada"),
    PUBLISH_ERROR("Error de publicación"),
    CANCELLED("Cancelada");

    private final String label;

    PublicationStatus(String label) { this.label = label; }

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

    public boolean canMoveTo(PublicationStatus next) { return NEXT.get(this).contains(next); }

    public String label() { return label; }
}
