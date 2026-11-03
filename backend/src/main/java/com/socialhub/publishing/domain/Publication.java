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
    /** Última propuesta de Claude, hasta que Raúl la corrige o la descarta. */
    private @Nullable String aiCaption;
    private @Nullable String aiHashtags;
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
                       boolean demo, long version, @Nullable String caption, @Nullable String hashtags,
                       @Nullable String aiCaption, @Nullable String aiHashtags) {
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
        this.aiCaption = aiCaption;
        this.aiHashtags = aiHashtags;
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
                blankToNull(details.hashtags()), null, null);
    }

    /** Fecha y hora de publicación en su zona horaria. */
    public Instant scheduledAt() {
        return scheduledOn.atTime(scheduledTime).atZone(ZoneId.of(timezone)).toInstant();
    }

    /** Raúl cambió un texto que propuso Claude: lo que proponía y lo que quedó. */
    public record TextCorrection(@Nullable String beforeCaption, @Nullable String afterCaption,
                                 @Nullable String beforeHashtags, @Nullable String afterHashtags) {}

    /**
     * Edita los datos. No se puede una vez que se está publicando; si está programada, respeta el margen.
     *
     * @return la corrección, si Raúl ha cambiado el texto o los hashtags que propuso Claude
     */
    public @Nullable TextCorrection edit(Details details, Instant now) {
        requireEditable();
        this.platform = details.platform();
        this.type = details.type().strip();
        this.title = details.title().strip();
        this.scheduledOn = details.scheduledOn();
        this.scheduledTime = details.scheduledTime();
        this.timezone = details.timezone();
        this.route = details.route().strip();
        this.note = blankToNull(details.note());
        String newCaption = blankToNull(details.caption());
        String newHashtags = blankToNull(details.hashtags());
        TextCorrection correction = null;
        boolean changed = !Objects.equals(newCaption, caption) || !Objects.equals(newHashtags, hashtags);
        if ((aiCaption != null || aiHashtags != null) && changed) {
            correction = new TextCorrection(aiCaption, newCaption, aiHashtags, newHashtags);
            aiCaption = null;
            aiHashtags = null;
        }
        this.caption = newCaption;
        this.hashtags = newHashtags;
        return correction;
    }

    /** Claude propone el texto y los hashtags: se guardan como propuesta para detectar las correcciones de Raúl. */
    public void proposeTexts(@Nullable String caption, @Nullable String hashtags) {
        setTexts(caption, hashtags);
        this.aiCaption = this.caption;
        this.aiHashtags = this.hashtags;
    }

    /** Raúl descarta el texto: se borran el texto, los hashtags y la propuesta de Claude. Devuelve lo descartado. */
    public String discardTexts() {
        if (caption == null && hashtags == null) throw new DomainException("No hay texto que descartar.");
        String discarded = (caption == null ? "" : caption) + (hashtags == null ? "" : "\n" + hashtags);
        setTexts(null, null);
        aiCaption = null;
        aiHashtags = null;
        return discarded.strip();
    }

    /**
     * Mientras está programada (o publicándose, o publicada) no se cambia nada: la vía ya tiene esta versión. Para
     * cambiarla, Raúl la vuelve a «Aprobada».
     */
    public void requireEditable() {
        if (status == PublicationStatus.SCHEDULED) {
            throw new DomainException("Está programada: para cambiarla, vuelve a «Aprobada» y prográmala otra vez.");
        }
        if (status == PublicationStatus.PUBLISHING || status == PublicationStatus.PUBLISHED) {
            throw new DomainException("No se cambia una publicación que se está publicando o ya está publicada.");
        }
    }

    /** Texto y hashtags propuestos (por Claude o por Raúl). No se cambian mientras está programada o publicándose. */
    public void setTexts(@Nullable String caption, @Nullable String hashtags) {
        requireEditable();
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

    public void flag(@Nullable String text) { this.flag = text; }
}
