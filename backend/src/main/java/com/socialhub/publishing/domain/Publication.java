package com.socialhub.publishing.domain;

import com.socialhub.platform.DomainException;
import java.time.Instant;
import java.util.Objects;

/** Una publicación de una promoción. Su estado solo cambia por transiciones permitidas. */
public final class Publication {

    private final String id;
    private final String promotionId;
    private PublicationStatus status;
    private String flag;
    private String externalId;
    private String confirmation;
    private Instant publishedAt;
    private final int version;

    public Publication(String id, String promotionId, PublicationStatus status, String flag, String externalId,
                       String confirmation, Instant publishedAt, int version) {
        this.id = Objects.requireNonNull(id);
        this.promotionId = promotionId;
        this.status = Objects.requireNonNull(status);
        this.flag = flag;
        this.externalId = externalId;
        this.confirmation = confirmation;
        this.publishedAt = publishedAt;
        this.version = version;
    }

    public void moveTo(PublicationStatus next) {
        if (next == PublicationStatus.PUBLISHED) {
            throw new DomainException("Una publicación solo pasa a «Publicada» con confirmación de la plataforma.");
        }
        if (!status.canMoveTo(next)) {
            throw new DomainException("No se puede pasar de «" + status.label() + "» a «" + next.label() + "».");
        }
        status = next;
    }

    /**
     * Confirma la publicación. Exige que se esté publicando y una prueba externa: el identificador de la
     * plataforma o la comprobación en la vista de Publer.
     */
    public void confirmPublished(String externalId, String confirmation, Instant at) {
        if (status != PublicationStatus.PUBLISHING) {
            throw new DomainException("Solo se confirma una publicación que se está publicando.");
        }
        if ((externalId == null || externalId.isBlank()) && (confirmation == null || confirmation.isBlank())) {
            throw new DomainException("Falta la confirmación de la plataforma.");
        }
        this.externalId = externalId;
        this.confirmation = confirmation == null || confirmation.isBlank() ? "ID externo " + externalId : confirmation;
        this.publishedAt = at;
        this.status = PublicationStatus.PUBLISHED;
    }

    public void flag(String text) { this.flag = text; }

    public String id() { return id; }
    public String promotionId() { return promotionId; }
    public PublicationStatus status() { return status; }
    public String flag() { return flag; }
    public String externalId() { return externalId; }
    public String confirmation() { return confirmation; }
    public Instant publishedAt() { return publishedAt; }
    public int version() { return version; }
}
