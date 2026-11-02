package com.socialhub.publishing.domain;

import com.socialhub.platform.DomainException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
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
    private Platform platform;
    private String type;
    private String title;
    private LocalDate scheduledOn;
    private LocalTime scheduledTime;
    private String timezone;
    private PublicationStatus status;
    private String route;
    private @Nullable String flag;
    private @Nullable String note;
    private @Nullable String caption;
    private @Nullable String hashtags;
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
                       boolean demo, long version, @Nullable String caption, @Nullable String hashtags) {
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
        this.caption = caption;
        this.hashtags = hashtags;
        if (status == PublicationStatus.PUBLISHED && (confirmation == null || confirmation.isBlank())) {
            throw new IllegalArgumentException("Una publicación publicada necesita confirmación");
        }
    }

    /** Margen mínimo entre ahora y la hora de publicación para poder programarla: nunca se publica al instante. */
    public static final Duration MIN_MARGIN = Duration.ofHours(6);

    /** Estados que solo pone SocialHub al publicar, nunca a mano. */
    private static final Set<PublicationStatus> AUTOMATIC =
            Set.of(PublicationStatus.PUBLISHING, PublicationStatus.PUBLISHED, PublicationStatus.PUBLISH_ERROR);

    /** Estados con los que se puede dar de alta una publicación: antes de la revisión. */
    public static final Set<PublicationStatus> INITIAL = Set.of(PublicationStatus.IDEA, PublicationStatus.PROPOSAL,
            PublicationStatus.IN_PRODUCTION, PublicationStatus.DRAFT);

    /** Datos editables de una publicación. */
    public record Details(Platform platform, String type, String title, LocalDate scheduledOn, LocalTime scheduledTime,
                          String timezone, String route, @Nullable String note, @Nullable String caption,
                          @Nullable String hashtags) {
        public Details {
            Objects.requireNonNull(platform);
            if (type.isBlank()) throw new IllegalArgumentException("Falta el tipo de publicación");
            if (title.isBlank()) throw new IllegalArgumentException("Falta el título");
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Zona horaria no válida: " + timezone);
            }
            if (route.isBlank()) throw new IllegalArgumentException("Falta la vía de publicación");
        }
    }

    /** Alta de una publicación nueva desde la app. */
    public static Publication create(String id, String promotionId, Details details, PublicationStatus status,
                                     boolean demo) {
        if (!INITIAL.contains(status)) {
            throw new IllegalArgumentException("Una publicación nueva empieza como idea, propuesta, en producción o borrador.");
        }
        return new Publication(id, promotionId, details.platform(), details.type().strip(), details.title().strip(),
                details.scheduledOn(), details.scheduledTime(), details.timezone(), status, details.route().strip(), null,
                blankToNull(details.note()), null, null, null, demo, 0, blankToNull(details.caption()),
                blankToNull(details.hashtags()));
    }

    /** Fecha y hora de publicación en su zona horaria. */
    public Instant scheduledAt() {
        return scheduledOn.atTime(scheduledTime).atZone(ZoneId.of(timezone)).toInstant();
    }

    /** Edita los datos. No se puede una vez que se está publicando; si está programada, respeta el margen. */
    public void edit(Details details, Instant now) {
        if (status == PublicationStatus.PUBLISHING || status == PublicationStatus.PUBLISHED) {
            throw new DomainException("No se puede editar una publicación que se está publicando o ya está publicada.");
        }
        Instant when = details.scheduledOn().atTime(details.scheduledTime()).atZone(ZoneId.of(details.timezone())).toInstant();
        if (status == PublicationStatus.SCHEDULED) requireMargin(when, now);
        this.platform = details.platform();
        this.type = details.type().strip();
        this.title = details.title().strip();
        this.scheduledOn = details.scheduledOn();
        this.scheduledTime = details.scheduledTime();
        this.timezone = details.timezone();
        this.route = details.route().strip();
        this.note = blankToNull(details.note());
        this.caption = blankToNull(details.caption());
        this.hashtags = blankToNull(details.hashtags());
    }

    /** Texto y hashtags propuestos (por Claude o por Raúl). No se cambian una vez que se está publicando. */
    public void setTexts(@Nullable String caption, @Nullable String hashtags) {
        if (status == PublicationStatus.PUBLISHING || status == PublicationStatus.PUBLISHED) {
            throw new DomainException("No se cambia el texto de una publicación que se está publicando o ya está publicada.");
        }
        this.caption = blankToNull(caption);
        this.hashtags = blankToNull(hashtags);
    }

    /** Cambio de estado pedido por Raúl. Los estados de publicación los pone solo SocialHub. */
    public void changeStatus(PublicationStatus next, Instant now) {
        if (AUTOMATIC.contains(next)) {
            throw new DomainException("«" + next.label() + "» solo lo pone SocialHub al publicar, con confirmación.");
        }
        if (next == PublicationStatus.SCHEDULED) requireMargin(scheduledAt(), now);
        moveTo(next);
    }

    private static void requireMargin(Instant when, Instant now) {
        if (when.isBefore(now.plus(MIN_MARGIN))) {
            throw new DomainException("Para programarla, la hora de publicación debe quedar al menos a "
                    + MIN_MARGIN.toHours() + " horas de ahora.");
        }
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
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
