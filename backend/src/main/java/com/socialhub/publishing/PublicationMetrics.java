package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.publishing.domain.MetricsRepository;
import com.socialhub.publishing.domain.MetricsRepository.Metrics;
import com.socialhub.publishing.domain.MetricsRepository.Status;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Métricas (fase 8, bloque 3): cada publicación se mide una vez, a los 7 días de publicarse. Sin Metricool, y la
 * API de Publora solo da estadísticas de Mastodon y Bluesky en planes de pago: las lee Claude con Chrome en el PC
 * de Raúl, con el encargo «Leer métricas». Lo que no se ve queda sin dato.
 */
@Slf4j
@Component
class PublicationMetrics implements AiTypeHandler {

    static final Duration AFTER = Duration.ofDays(7);
    static final Duration RETRY_AFTER = Duration.ofDays(1);
    static final int MAX_ATTEMPTS = 3;
    static final int MAX_PER_ORDER = 10;
    static final long MAX_VALUE = 1_000_000_000L;

    private final PublicationRepository publications;
    private final MetricsRepository metrics;
    private final PublishJobRepository jobs;
    private final AiOrders orders;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PublicationMetrics(PublicationRepository publications, MetricsRepository metrics, PublishJobRepository jobs,
                       AiOrders orders, TransactionTemplate transactions, ApplicationEventPublisher events, Clock clock) {
        this.publications = publications;
        this.metrics = metrics;
        this.jobs = jobs;
        this.orders = orders;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    Optional<Metrics> of(String publicationId) { return metrics.find(publicationId); }

    // ------------------------------------------------------------------ Reloj

    @Scheduled(initialDelayString = "${socialhub.metrics.initial-delay-millis:180000}",
               fixedDelayString = "${socialhub.metrics.tick-millis:3600000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                requestDue();
            } catch (RuntimeException e) {
                log.warn("Métricas omitidas: {}", e.toString());
            }
        });
    }

    /** Pide las métricas de lo publicado hace 7 días o más que aún no se ha medido. @return encargos creados */
    int requestDue() {
        Instant now = clock.instant();
        Map<String, Metrics> known = new LinkedHashMap<>();
        metrics.all().forEach(m -> known.put(m.publicationId(), m));
        Map<String, List<Publication>> due = new LinkedHashMap<>();
        for (Publication p : publications.findAll()) {
            if (p.isDemo() || p.getStatus() != PublicationStatus.PUBLISHED || p.getPublishedAt() == null) continue;
            if (now.isBefore(p.getPublishedAt().plus(AFTER))) continue;
            Metrics m = known.get(p.getId());
            boolean retry = m != null && m.status() == Status.FAILED && m.attempts() < MAX_ATTEMPTS
                    && !now.isBefore(m.requestedAt().plus(RETRY_AFTER));
            if (m == null || retry) due.computeIfAbsent(p.getPromotionId(), k -> new ArrayList<>()).add(p);
        }
        int n = 0;
        for (var entry : due.entrySet()) {
            List<Publication> all = entry.getValue();
            for (int from = 0; from < all.size(); from += MAX_PER_ORDER) {
                List<Publication> batch = all.subList(from, Math.min(all.size(), from + MAX_PER_ORDER));
                try {
                    transactions.executeWithoutResult(s -> {
                        List<String> ids = batch.stream().map(Publication::getId).toList();
                        String task = orders.submitInternal(entry.getKey(), AiRequestType.METRICS_READ,
                                "Leer métricas de " + ids.size() + (ids.size() == 1 ? " publicación" : " publicaciones"),
                                "Lee las estadísticas de estas publicaciones de hace una semana en Instagram o TikTok.", ids);
                        for (Publication p : batch) {
                            Metrics before = known.get(p.getId());
                            metrics.save(new Metrics(p.getId(), Status.REQUESTED, task, before == null ? 1 : before.attempts() + 1,
                                    now, null, null, null, null, null, null, null, null));
                        }
                    });
                    n++;
                } catch (RuntimeException e) {
                    log.warn("No se pudieron pedir las métricas de {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Encargo «Leer métricas»

    @Override public AiRequestType type() { return AiRequestType.METRICS_READ; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.isEmpty()) throw new IllegalArgumentException("El encargo lleva las publicaciones");
        for (String id : inputs) {
            publications.findById(id).filter(p -> p.getPromotionId().equals(promotionId))
                    .orElseThrow(() -> new IllegalArgumentException("La publicación «" + id + "» no existe en esta promoción"));
        }
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : job.inputs()) {
            publications.findById(id).ifPresent(p -> {
                Map<String, Object> m = new LinkedHashMap<>(PublishingQueries.forAi(p));
                if (p.getPublishedAt() != null) m.put("published_at", p.getPublishedAt().toString());
                jobs.find(id).map(j -> j.getUrl()).ifPresent(url -> m.put("url", url));
                list.add(m);
            });
        }
        return Map.of(
                "publications", list,
                "how", List.of(
                        "Abre cada publicación en Chrome con la sesión de Raúl (su «url» o búscala en el perfil de la marca) y mira sus estadísticas.",
                        "Apunta solo lo que veas: reproducciones, alcance (cuentas alcanzadas), me gusta, comentarios, guardados y compartidos.",
                        "Lo que no se vea, déjalo en null. Nunca estimes ni inventes una cifra.",
                        "Solo lectura: no comentes, no des me gusta, no compartas y no cambies nada."),
                "expected_outputs", "metrics: [{publication_id, views, reach, likes, comments, saves, shares (números o null), "
                        + "note}] para cada publicación de la lista, y summary");
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode items = outputs.path("metrics");
        if (!items.isArray()) throw new IllegalArgumentException("Falta «outputs.metrics»");
        Set<String> asked = new HashSet<>(job.inputs());
        Instant now = clock.instant();
        List<Metrics> read = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode i : items) {
            String id = i.path("publication_id").asString("");
            if (!asked.contains(id)) throw new IllegalArgumentException("Métricas de una publicación que no se pidió: «" + id + "»");
            if (!seen.add(id)) throw new IllegalArgumentException("Métricas repetidas de «" + id + "»");
            int attempts = metrics.find(id).map(Metrics::attempts).orElse(1);
            String note = i.path("note").isString() ? i.path("note").asString().strip() : null;
            read.add(new Metrics(id, Status.DONE, job.taskId(), attempts, metrics.find(id).map(Metrics::requestedAt).orElse(now), now,
                    count(i, "views", id), count(i, "reach", id), count(i, "likes", id), count(i, "comments", id),
                    count(i, "saves", id), count(i, "shares", id), note == null || note.isEmpty() ? null : note));
        }
        read.forEach(metrics::save);
        for (String id : asked) {
            if (!seen.contains(id)) failed(id, job.taskId(), "Claude no devolvió sus métricas", now);
        }
        read.forEach(m -> events.publishEvent(new FactoryChanged("publication", m.publicationId())));
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return "Métricas guardadas de " + read.size() + (read.size() == 1 ? " publicación" : " publicaciones")
                + (summary.isEmpty() ? "" : " · " + summary);
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        Instant now = clock.instant();
        job.inputs().forEach(id -> failed(id, job.taskId(), reason, now));
    }

    private void failed(String id, String taskId, String reason, Instant now) {
        metrics.find(id).filter(m -> taskId.equals(m.taskId()) && m.status() == Status.REQUESTED).ifPresent(m ->
                metrics.save(new Metrics(id, Status.FAILED, taskId, m.attempts(), m.requestedAt(), null, null, null, null,
                        null, null, null, reason.length() > 500 ? reason.substring(0, 500) : reason)));
    }

    private static @Nullable Long count(JsonNode item, String field, String id) {
        JsonNode v = item.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        if (!v.isIntegralNumber() || v.asLong() < 0 || v.asLong() > MAX_VALUE) {
            throw new IllegalArgumentException("«" + field + "» de «" + id + "» tiene que ser un número entero o null");
        }
        return v.asLong();
    }

    /** Para el balance semanal: las métricas medidas de esas publicaciones. */
    Map<String, Object> forAi(String publicationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        metrics.find(publicationId).filter(x -> x.status() == Status.DONE).ifPresent(x -> {
            m.put("measured_at", x.measuredAt() == null ? null : x.measuredAt().toString());
            m.put("views", x.views());
            m.put("reach", x.reach());
            m.put("likes", x.likes());
            m.put("comments", x.comments());
            m.put("saves", x.saves());
            m.put("shares", x.shares());
        });
        return m;
    }
}
