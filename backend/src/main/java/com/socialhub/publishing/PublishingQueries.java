package com.socialhub.publishing;

import com.socialhub.platform.NotFoundException;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationFilter;
import com.socialhub.publishing.domain.PublicationPage;
import com.socialhub.publishing.domain.PublicationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishingQueries {

    private final PublicationRepository publications;
    private final PublicationFileRepository files;
    private final com.socialhub.publishing.domain.PublishJobRepository jobs;
    private final List<PublishRoute> routes;
    private final com.socialhub.publishing.domain.MetricsRepository metricsRepository;
    private final PubloraService publora;
    private final com.socialhub.promotion.PromotionQueries promotionQueries;

    /** Vía de publicación y si está lista. */
    public record RouteState(PublishRoute.Id id, String label, boolean configured) {}

    /** Aviso de publicación para «Necesita atención». */
    public record PublishAlert(String publicationId, boolean failed, String title, String detail) {}

    /**
     * Una publicación de camino a publicar a la que Publora no puede servir de vía: {@code gap} dice qué falta y
     * {@code platform} es la red, en español.
     */
    public record Unroutable(String promotionId, java.time.LocalDate scheduledOn, String platform, PubloraService.Gap gap) {}

    /** Estados de «camino a publicar»: aún no programadas en una vía. */
    private static final java.util.Set<com.socialhub.publishing.domain.PublicationStatus> ON_THE_WAY = java.util.EnumSet.of(
            com.socialhub.publishing.domain.PublicationStatus.IN_PRODUCTION, com.socialhub.publishing.domain.PublicationStatus.DRAFT,
            com.socialhub.publishing.domain.PublicationStatus.IN_REVIEW, com.socialhub.publishing.domain.PublicationStatus.APPROVED);

    /**
     * Publicaciones de camino a publicar (en producción, borrador, en revisión o aprobadas) con fecha entre esos días (ambos
     * incluidos) que Publora no puede publicar: sin clave, sin cuenta de su red para su marca o con «No uso Publora».
     */
    public List<Unroutable> withoutPubloraRoute(java.time.LocalDate from, java.time.LocalDate to,
                                                java.util.function.Function<String, String> brandOfPromotion) {
        List<Unroutable> result = new java.util.ArrayList<>();
        java.util.Map<String, PubloraService.Gap> cache = new java.util.HashMap<>();
        for (Publication p : publications.findAll()) {
            if (!ON_THE_WAY.contains(p.getStatus()) || p.getScheduledOn().isBefore(from) || p.getScheduledOn().isAfter(to)) continue;
            String brand = brandOfPromotion.apply(p.getPromotionId());
            if (brand == null) continue;
            var gap = cache.computeIfAbsent(brand + "|" + p.getPlatform(), k -> publora.gap(brand, p.getPlatform()));
            if (gap != PubloraService.Gap.NONE) {
                result.add(new Unroutable(p.getPromotionId(), p.getScheduledOn(),
                        p.getPlatform() == com.socialhub.publishing.domain.Platform.INSTAGRAM ? "Instagram" : "TikTok", gap));
            }
        }
        return result;
    }

    /** Métricas de la publicación, si ya se pidieron. */
    public java.util.Optional<com.socialhub.publishing.domain.MetricsRepository.Metrics> metrics(String publicationId) {
        if (!publications.exists(publicationId)) throw new NotFoundException("No existe la publicación " + publicationId);
        return metricsRepository.find(publicationId);
    }

    /** Métricas agregadas de la promoción (solo con las publicaciones medidas). */
    public com.socialhub.publishing.domain.PromotionMetricsReport promotionMetrics(String promotionId) {
        promotionQueries.brandOf(promotionId); // 404 si la promoción no existe
        var metrics = new java.util.HashMap<String, com.socialhub.publishing.domain.MetricsRepository.Metrics>();
        metricsRepository.all().forEach(m -> metrics.put(m.publicationId(), m));
        var mine = publications.findAll().stream().filter(p -> p.getPromotionId().equals(promotionId)).toList();
        return com.socialhub.publishing.domain.PromotionMetricsReport.of(mine, metrics);
    }

    /** Cómo va la publicación (solo si alguna vez se programó). */
    public java.util.Optional<com.socialhub.publishing.domain.PublishJob> publishJob(String publicationId) {
        if (!publications.exists(publicationId)) throw new NotFoundException("No existe la publicación " + publicationId);
        return jobs.find(publicationId);
    }

    public List<RouteState> routes() {
        return routes.stream().sorted(java.util.Comparator.comparing(PublishRoute::id))
                .map(r -> new RouteState(r.id(), r.id().label(), r.configured())).toList();
    }

    /**
     * Lo que Raúl tiene que mirar: publicaciones para hacer a mano, con error de publicación o con un posible
     * duplicado en otra vía.
     */
    public List<PublishAlert> publishAlerts() {
        List<PublishAlert> alerts = new java.util.ArrayList<>();
        java.util.Set<String> listed = new java.util.HashSet<>();
        var statuses = java.util.EnumSet.of(com.socialhub.publishing.domain.PublishJob.Status.MANUAL,
                com.socialhub.publishing.domain.PublishJob.Status.FAILED);
        for (var job : jobs.withStatus(statuses)) {
            publications.findById(job.getPublicationId()).ifPresent(p -> {
                String flag = p.getFlag();
                if (job.getStatus() == com.socialhub.publishing.domain.PublishJob.Status.MANUAL) {
                    String official = flag != null && flag.startsWith(PublishingEngine.OFFICIAL_FLAG) ? " · " + flag : "";
                    alerts.add(new PublishAlert(p.getId(), false, "Publicar a mano: " + p.getTitle(),
                            "Ninguna vía pudo programarla a tiempo. LEEME: " + job.getManualFile() + official));
                    listed.add(p.getId());
                } else if (p.getStatus() == com.socialhub.publishing.domain.PublicationStatus.PUBLISH_ERROR) {
                    // Con duda o con motivo de la API oficial, se enseña el aviso entero («mira en Instagram…»)
                    boolean doubt = flag != null && flag.startsWith(PublishingEngine.DOUBT_FLAG);
                    boolean official = flag != null && flag.startsWith(PublishingEngine.OFFICIAL_FLAG);
                    alerts.add(new PublishAlert(p.getId(), true, (doubt ? "Revisar antes de reintentar: " : "Error de publicación: ")
                            + p.getTitle(), doubt || official ? flag : job.getLastError() == null ? "Revisa la publicación" : job.getLastError()));
                    listed.add(p.getId());
                }
            });
        }
        for (Publication p : publications.findAll()) {
            String flag = p.getFlag();
            if (flag == null || listed.contains(p.getId())) continue;
            if (flag.startsWith(PublishingEngine.LATE_FLAG) || flag.startsWith(PublishingEngine.STILL_SCHEDULED)) {
                alerts.add(new PublishAlert(p.getId(), false, "Revisa la programación: " + p.getTitle(), flag));
            } else if (flag.startsWith(PublishingEngine.DOUBT_FLAG)) {
                alerts.add(new PublishAlert(p.getId(), true, "Revisar antes de reintentar: " + p.getTitle(), flag));
            } else if (flag.startsWith(PublishingEngine.OFFICIAL_FLAG)
                    && p.getStatus() != com.socialhub.publishing.domain.PublicationStatus.CANCELLED) {
                alerts.add(new PublishAlert(p.getId(), false, "La API oficial de Instagram no pudo: " + p.getTitle(), flag));
            } else if (flag.startsWith(PublishingEngine.DELAY_FLAG)) {
                alerts.add(new PublishAlert(p.getId(), false, "Publicada con retraso: " + p.getTitle(), flag));
            }
        }
        return alerts;
    }

    public PublicationPage page(PublicationFilter filter, long offset, int limit) {
        return publications.find(filter, offset, limit);
    }

    public List<Publication> all() { return publications.findAll(); }

    /** Cuántas publicaciones en estado «Propuesta» hay por promoción (solo las promociones que tienen alguna). */
    public java.util.Map<String, Long> proposalCounts() {
        return publications.countByPromotion(com.socialhub.publishing.domain.PublicationStatus.PROPOSAL);
    }

    /** La publicación existe y es de esa promoción. */
    public boolean belongsTo(String promotionId, String publicationId) {
        return publications.findById(publicationId).map(p -> p.getPromotionId().equals(promotionId)).orElse(false);
    }

    public List<PublicationFile> files(String publicationId) {
        if (!publications.exists(publicationId)) throw new NotFoundException("No existe la publicación " + publicationId);
        return files.files(publicationId);
    }

    public java.util.Optional<Publication> find(String id) { return publications.findById(id); }

    /** Publicaciones de una promoción entre dos fechas (incluidas), por fecha y hora. */
    public List<Publication> between(String promotionId, java.time.LocalDate from, java.time.LocalDate to) {
        return publications.find(new com.socialhub.publishing.domain.PublicationFilter(promotionId, java.util.Set.of(), null,
                from, to, null), 0, 500).items();
    }

    /** Publicaciones aprobadas entre dos fechas (sin ideas, propuestas ni canceladas), para el spec semanal. */
    public List<java.util.Map<String, Object>> approvedForAi(String promotionId, java.time.LocalDate from, java.time.LocalDate to) {
        var excluded = java.util.EnumSet.of(com.socialhub.publishing.domain.PublicationStatus.IDEA,
                com.socialhub.publishing.domain.PublicationStatus.PROPOSAL, com.socialhub.publishing.domain.PublicationStatus.CANCELLED);
        return between(promotionId, from, to).stream().filter(p -> !excluded.contains(p.getStatus()))
                .map(PublishingQueries::forAi).toList();
    }

    /** Una publicación a la espera de contenido: su identificador y la hora a la que se publica. */
    public record AwaitingContent(String id, java.time.Instant scheduledAt) {}

    /** Publicaciones «En producción» de la promoción que aún no tienen ningún archivo (piezas), por fecha y hora. */
    public List<AwaitingContent> awaitingContent(String promotionId) {
        return publications.findAll().stream()
                .filter(p -> p.getPromotionId().equals(promotionId)
                        && p.getStatus() == com.socialhub.publishing.domain.PublicationStatus.IN_PRODUCTION
                        && files.files(p.getId()).isEmpty())
                .sorted(java.util.Comparator.comparing(Publication::scheduledAt))
                .map(p -> new AwaitingContent(p.getId(), p.scheduledAt()))
                .toList();
    }

    /** Publicaciones de esas ids, como datos sencillos para un encargo a Claude (las que no existen se omiten). */
    public List<java.util.Map<String, Object>> forAi(java.util.Collection<String> ids) {
        return ids.stream().map(publications::findById).flatMap(java.util.Optional::stream)
                .map(PublishingQueries::forAi).toList();
    }

    /** Publicaciones entre dos fechas, como datos sencillos para un encargo a Claude. */
    public List<java.util.Map<String, Object>> forAi(String promotionId, java.time.LocalDate from, java.time.LocalDate to) {
        return between(promotionId, from, to).stream().map(PublishingQueries::forAi).toList();
    }

    static java.util.Map<String, Object> forAi(Publication p) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("id", p.getId());
        m.put("platform", p.getPlatform().name());
        m.put("type", p.getType());
        m.put("title", p.getTitle());
        m.put("scheduled_on", p.getScheduledOn().toString());
        m.put("scheduled_time", p.getScheduledTime().toString());
        m.put("status", p.getStatus().name());
        if (p.getNote() != null) m.put("note", p.getNote());
        if (p.getFlag() != null) m.put("flag", p.getFlag());
        if (p.getConfirmation() != null) m.put("confirmation", p.getConfirmation());
        if (p.getCaption() != null) m.put("caption", p.getCaption());
        if (p.getHashtags() != null) m.put("hashtags", p.getHashtags());
        return m;
    }
}
