package com.socialhub.publishing;

import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFilter;
import com.socialhub.publishing.domain.PublicationPage;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consultas de publicaciones (solo lectura). */
@PrimaryPort
public interface PublishingQueries {

    /** Vía de publicación y si está lista. */
    record RouteState(PublishRouteId id, String label, boolean configured) {}

    /** Aviso de publicación para «Necesita atención». */
    record PublishAlert(String publicationId, boolean failed, String title, String detail) {}

    /**
     * Una publicación de camino a publicar a la que Publora no puede servir de vía: {@code gap} dice qué falta y
     * {@code platform} es la red, en español.
     */
    record Unroutable(String promotionId, java.time.LocalDate scheduledOn, String platform, PubloraConnection.Gap gap) {}

    /**
     * Publicaciones de camino a publicar (en producción, borrador, en revisión o aprobadas) con fecha entre esos días (ambos
     * incluidos) que Publora no puede publicar: sin clave, sin cuenta de su red para su marca o con «No uso Publora».
     */
    List<Unroutable> withoutPubloraRoute(java.time.LocalDate from, java.time.LocalDate to, java.util.function.Function<String, String> brandOfPromotion);

    /** Una publicación de camino a publicar cuya hora ya no deja el margen mínimo para programarla. */
    record WithoutMargin(String promotionId, String publicationId, String title, java.time.Instant scheduledAt) {}

    /**
     * Publicaciones de camino a publicar (en producción, borrador, en revisión o aprobadas) a las que ya no les quedan
     * {@link Publication#MIN_MARGIN} hasta su hora (o cuya hora ya pasó), por hora de publicación.
     */
    List<WithoutMargin> withoutMargin(java.time.Instant now);

    /** Por qué una publicación puede moverse a la siguiente hora libre. */
    enum MoveReason { LOST_MARGIN, CLASH }

    /** Aviso de posible duplicado: la otra publicación y por qué se parecen. */
    record DuplicateRef(String publicationId, com.socialhub.publishing.domain.DuplicateDetector.Reason reason) {}

    /**
     * Lo que se ve de cada publicación además de sus datos: cómo ha acabado (vacío si está cancelada), con qué otras se
     * repite y por qué puede moverse a la siguiente hora libre (vacío si no hace falta o no se puede mover).
     */
    record Insight(java.util.Optional<com.socialhub.publishing.domain.PublicationOutcome> outcome, List<DuplicateRef> duplicates,
                   java.util.Optional<MoveReason> move) {}

    /** Lo anterior de todas las publicaciones, por identificador. */
    java.util.Map<String, Insight> insights();

    /** Grupos de posibles duplicados (publicaciones activas de la misma marca y red) de todas las promociones. */
    List<com.socialhub.publishing.domain.DuplicateDetector.Group> duplicateGroups();

    /** Hora propuesta para mover una publicación. */
    record SlotProposal(String publicationId, MoveReason reason, java.time.LocalDate currentDay, java.time.LocalTime currentTime,
                        java.time.LocalDate newDay, java.time.LocalTime newTime, String timezone, boolean changesDay,
                        java.time.@org.jspecify.annotations.Nullable LocalDate promotionEndsOn, boolean afterPromotionEnd,
                        long marginMinutes) {}

    /**
     * La siguiente hora libre de una publicación que ha perdido el margen o choca con otra. Falla (409) si no se puede
     * mover (programada, publicada…) o no hace falta.
     */
    SlotProposal nextFreeSlot(String publicationId);

    /** Métricas de la publicación, si ya se pidieron. */
    java.util.Optional<com.socialhub.publishing.domain.MetricsRepository.Metrics> metrics(String publicationId);

    /** Métricas agregadas de la promoción (solo con las publicaciones medidas). */
    com.socialhub.publishing.domain.PromotionMetricsReport promotionMetrics(String promotionId);

    /** Cómo va la publicación (solo si alguna vez se programó). */
    java.util.Optional<com.socialhub.publishing.domain.PublishJob> publishJob(String publicationId);

    List<RouteState> routes();

    /**
     * Lo que Raúl tiene que mirar: publicaciones para hacer a mano, con error de publicación o con un posible
     * duplicado en otra vía.
     */
    List<PublishAlert> publishAlerts();

    PublicationPage page(PublicationFilter filter, long offset, int limit);

    List<Publication> all();

    /** Cuántas publicaciones en estado «Propuesta» hay por promoción (solo las promociones que tienen alguna). */
    java.util.Map<String, Long> proposalCounts();

    /** La publicación existe y es de esa promoción. */
    boolean belongsTo(String promotionId, String publicationId);

    List<PublicationFile> files(String publicationId);

    java.util.Optional<Publication> find(String id);

    /** Publicaciones de una promoción entre dos fechas (incluidas), por fecha y hora. */
    List<Publication> between(String promotionId, java.time.LocalDate from, java.time.LocalDate to);

    /** Publicaciones aprobadas entre dos fechas (sin ideas, propuestas ni canceladas), para el spec semanal. */
    List<java.util.Map<String, Object>> approvedForAi(String promotionId, java.time.LocalDate from, java.time.LocalDate to);

    /** Una publicación a la espera de contenido: su identificador y la hora a la que se publica. */
    record AwaitingContent(String id, String title, java.time.Instant scheduledAt) {}

    /** Publicaciones «En producción» de la promoción que aún no tienen ningún archivo (piezas), por fecha y hora. */
    List<AwaitingContent> awaitingContent(String promotionId);

    /** La hora de publicación más próxima de esas publicaciones (se omiten las que no existen o están canceladas). */
    java.util.Optional<java.time.Instant> earliestScheduled(java.util.Collection<String> ids);

    /** Publicaciones de esas ids, como datos sencillos para un encargo a Claude (se omiten las que no existen o están canceladas). */
    List<java.util.Map<String, Object>> forAi(java.util.Collection<String> ids);

    /** Publicaciones entre dos fechas, como datos sencillos para un encargo a Claude. */
    List<java.util.Map<String, Object>> forAi(String promotionId, java.time.LocalDate from, java.time.LocalDate to);
}
