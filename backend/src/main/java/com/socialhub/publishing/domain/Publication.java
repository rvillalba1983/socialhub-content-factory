package com.socialhub.publishing.domain;

import com.socialhub.platform.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/** Una publicación de una promoción. Su estado solo cambia por transiciones permitidas. */
@AggregateRoot
@Getter
public final class Publication {

    @Identity
    private final String id;
    private final String promotionId;
    private final Platform platform;
    private final String type;
    private final String title;
    private final LocalDate scheduledOn;
    private final LocalTime scheduledTime;
    private final String timezone;
    private PublicationStatus status;
    private final String route;
    private @Nullable String flag;
    private final @Nullable String note;
    private @Nullable String externalId;
    private @Nullable String confirmation;
    private @Nullable Instant publishedAt;
    private final boolean demo;
    private final long version;

    @Builder
    public Publication(String id, String promotionId, Platform platform, String type, String title,
                       LocalDate scheduledOn, LocalTime scheduledTime, @Nullable String timezone,
                       PublicationStatus status, @Nullable String route, @Nullable String flag, @Nullable String note,
                       @Nullable String externalId, @Nullable String confirmation, @Nullable Instant publishedAt,
                       boolean demo, long version) {
        this.id = Objects.requireNonNull(id);
        this.promotionId = Objects.requireNonNull(promotionId);
        this.platform = Objects.requireNonNull(platform);
        this.type = Objects.requireNonNull(type);
        this.title = Objects.requireNonNull(title);
        this.scheduledOn = Objects.requireNonNull(scheduledOn);
        this.scheduledTime = Objects.requireNonNull(scheduledTime);
        this.timezone = timezone == null ? "Europe/Madrid" : timezone;
        this.status = Objects.requireNonNull(status);
        this.route = route == null ? "Publer" : route;
        this.flag = flag;
        this.note = note;
        this.externalId = externalId;
        this.confirmation = confirmation;
        this.publishedAt = publishedAt;
        this.demo = demo;
        this.version = version;
        if (status == PublicationStatus.PUBLISHED && (confirmation == null || confirmation.isBlank())) {
            throw new IllegalArgumentException("Una publicación publicada necesita confirmación");
        }
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
    public void confirmPublished(@Nullable String externalId, @Nullable String confirmation, Instant at) {
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
}
