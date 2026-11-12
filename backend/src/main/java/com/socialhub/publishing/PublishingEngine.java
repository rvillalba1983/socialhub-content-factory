package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Motor de publicación (fase 7). Al pasar una publicación a «Programada» prueba las vías por orden: Publer, Publora
 * y API oficial. Si fallan todas, reintenta cada hora mientras quede el margen de 6 h; después deja un LEEME para
 * publicarla a mano y avisa. Pasada la hora, la pasa a «Publicando» y pide la comprobación: «Publicada» solo llega
 * con el enlace o el identificador; si no aparece, «Error de publicación».
 */
@Slf4j
@Service
class PublishingEngine {

    static final Duration RETRY = Duration.ofHours(1);
    /** Tras la hora, cuándo se comprueba (a mano se da más tiempo a Raúl). */
    static final Duration CHECK_AFTER = Duration.ofMinutes(15);
    static final Duration CHECK_AFTER_MANUAL = Duration.ofHours(2);
    /** Si la comprobación no llega en este tiempo, queda en error. */
    static final Duration CHECK_TIMEOUT = Duration.ofHours(24);
    /** Cada cuánto se vuelve a consultar una vía con API mientras sigue publicando. */
    static final Duration RECHECK = Duration.ofMinutes(5);
    static final String LATE_FLAG = "Posible duplicado: ";
    static final String STILL_SCHEDULED = "Estaba programada en ";
    static final String ERROR_FLAG = "Error de publicación: ";
    /** La API oficial no pudo y se sigue por otra vía (el motivo queda visible). */
    static final String OFFICIAL_FLAG = "API oficial: ";
    /** No se sabe si Instagram llegó a publicarla: no se reintenta, se revisa. */
    static final String DOUBT_FLAG = "Revisar antes de reintentar: ";
    /** Pasado este tiempo desde la hora, la API oficial ya no publica (SocialHub estaba cerrado). */
    static final Duration OFFICIAL_GRACE = Duration.ofMinutes(10);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));
    private static final EnumSet<PublishJob.Status> TIMED = EnumSet.of(PublishJob.Status.WORKING,
            PublishJob.Status.RETRY_WAIT, PublishJob.Status.SCHEDULED, PublishJob.Status.MANUAL, PublishJob.Status.CHECKING);

    private final PublishJobRepository jobs;
    private final PublicationRepository publications;
    private final PublicationFileRepository files;
    private final PromotionQueries promotions;
    private final List<PublishRoute> routes;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final AiOrders orders;
    private final Clock clock;

    PublishingEngine(PublishJobRepository jobs, PublicationRepository publications, PublicationFileRepository files,
                     PromotionQueries promotions, List<PublishRoute> routes, ApplicationEventPublisher events,
                     TransactionTemplate transactions, @Lazy AiOrders orders, Clock clock) {
        this.orders = orders;
        this.jobs = jobs;
        this.publications = publications;
        this.files = files;
        this.promotions = promotions;
        this.routes = routes.stream().sorted(Comparator.comparing(PublishRoute::id)).toList();
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    List<PublishRoute> routes() { return routes; }

    Optional<PublishJob> job(String publicationId) { return jobs.find(publicationId); }

    /** Llegaron enlaces públicos cuando ya no hacían falta: se borran las imágenes que se dejaron. */
    void discardPublicMedia(String publicationId) {
        publications.findById(publicationId).ifPresent(this::cleanup);
    }

    // ------------------------------------------------------------------ Lo que pide Raúl

    /** Raúl la ha pasado a «Programada» (dentro de su transacción; la publicación la guarda quien llama). */
    void start(Publication p) {
        Instant now = clock.instant();
        long version = jobs.find(p.getId()).map(PublishJob::getVersion).orElse(0L);
        PublishJob job = PublishJob.start(p.getId(), now, version);
        if (ownFlag(p.getFlag())) p.flag(null);
        advance(job, p, now);
        jobs.save(job);
    }

    /**
     * Raúl la ha sacado de «Programada»: se deja de publicar. Si ya estaba programada en una vía, hay que quitarla
     * allí. La publicación la guarda quien llama.
     */
    void stop(Publication p, String reason) {
        jobs.find(p.getId()).filter(j -> j.getStatus().open()).ifPresent(job -> {
            Instant now = clock.instant();
            boolean scheduledSomewhere = job.getStatus() == PublishJob.Status.SCHEDULED;
            String route = job.getRoute();
            job.cancel(reason, now);
            if (scheduledSomewhere) {
                PublishRoute owner = route(route);
                boolean removed;
                try {
                    removed = owner != null && owner.unschedule(p, job);
                } catch (RuntimeException e) {
                    removed = false;
                    job.note(now, route + ": no se pudo quitar (" + e.getMessage() + ")");
                }
                if (removed) {
                    job.note(now, "Quitada de " + route);
                } else {
                    p.flag(STILL_SCHEDULED + route + ": bórrala allí a mano");
                    job.note(now, "Sigue programada en " + route + ": hay que borrarla allí a mano");
                }
            }
            cleanup(p);
            jobs.save(job);
        });
    }

    /** Incidencias que anota el motor: al volver a programarla se quitan. */
    static boolean ownFlag(@Nullable String flag) {
        return flag != null && (flag.startsWith(LATE_FLAG) || flag.startsWith(STILL_SCHEDULED)
                || flag.startsWith(ERROR_FLAG) || flag.startsWith(OFFICIAL_FLAG) || flag.startsWith(DOUBT_FLAG)
                || flag.startsWith(PublishingService.MANUAL_FLAG));
    }

    // ------------------------------------------------------------------ Resultados de las vías

    /** Una vía la ha programado (por ejemplo, Claude en Publer). */
    String scheduled(String publicationId, String taskId, @Nullable String externalId, @Nullable String url) {
        PublishJob job = jobs.find(publicationId).orElseThrow(() -> new IllegalStateException("No hay publicación en marcha para " + publicationId));
        Publication p = publications.findById(publicationId).orElseThrow();
        Instant now = clock.instant();
        if (!taskId.equals(job.getTaskId())) {
            String flag = LATE_FLAG + "se programó tarde o ya no hacía falta (" + taskId + "). Revisa Publer y bórrala allí si sobra";
            p.flag(flag);
            publications.save(p);
            job.note(now, flag);
            jobs.save(job);
            changed(publicationId);
            return flag;
        }
        job.scheduled(externalId, url, now);
        jobs.save(job);
        changed(publicationId);
        return "Programada en " + job.getRoute() + " para el " + WHEN.format(p.scheduledAt());
    }

    /** Una vía no pudo (o Claude no pudo hacer el encargo). */
    void routeFailed(String publicationId, String taskId, String reason) {
        jobs.find(publicationId).ifPresent(job -> {
            if (!taskId.equals(job.getTaskId())) return; // encargo viejo: ya se siguió por otro camino
            Publication p = publications.findById(publicationId).orElseThrow();
            Instant now = clock.instant();
            if (job.getStatus() == PublishJob.Status.CHECKING) {
                fail(job, p, "No se pudo comprobar: " + reason, now);
            } else {
                if (isOfficial(job.getRoute())) p.flag(OFFICIAL_FLAG + reason + ": se sigue por otra vía");
                cleanup(p);
                job.routeFailed(reason, now);
                advance(job, p, now);
            }
            publications.save(p);
            jobs.save(job);
            changed(publicationId);
        });
    }

    /** La comprobación: está publicada (con enlace o identificador) o no aparece. */
    String checked(String publicationId, String taskId, boolean published, @Nullable String externalId,
                   @Nullable String url, String note) {
        PublishJob job = jobs.find(publicationId).orElseThrow(() -> new IllegalStateException("No hay publicación en marcha para " + publicationId));
        if (!taskId.equals(job.getTaskId()) || job.getStatus() != PublishJob.Status.CHECKING) {
            return "Comprobación que ya no hacía falta: no cambia nada";
        }
        Publication p = publications.findById(publicationId).orElseThrow();
        Instant now = clock.instant();
        String result;
        if (published) {
            p.confirmPublished(externalId, url, now);
            job.published(externalId, url, now);
            cleanup(p);
            result = "Publicada" + (url == null ? "" : ": " + url);
        } else {
            fail(job, p, note.isBlank() ? "Claude no la encuentra publicada" : note, now);
            result = "No aparece publicada: " + job.getLastError();
        }
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
        return result;
    }

    // ------------------------------------------------------------------ Reloj

    @Scheduled(initialDelayString = "${socialhub.publishing.initial-delay-millis:30000}",
               fixedDelayString = "${socialhub.publishing.tick-millis:60000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                tick();
            } catch (RuntimeException e) {
                log.warn("Motor de publicación: {}", e.toString());
            }
        });
    }

    /** Hace lo que toque por la hora en cada publicación en marcha. Cada una, en su transacción. */
    int tick() {
        int changed = 0;
        for (PublishJob listed : jobs.withStatus(TIMED)) {
            try {
                if (listed.getStatus() == PublishJob.Status.SCHEDULED && isOfficial(listed.getRoute())
                        && publishOfficially(listed.getPublicationId())) {
                    changed++;
                    continue;
                }
                Boolean done = transactions.execute(s -> step(listed.getPublicationId()));
                if (Boolean.TRUE.equals(done)) changed++;
            } catch (RuntimeException e) {
                log.warn("Publicación {}: {}", listed.getPublicationId(), e.toString());
            }
        }
        return changed;
    }

    private boolean step(String publicationId) {
        PublishJob job = jobs.find(publicationId).orElse(null);
        Publication p = publications.findById(publicationId).orElse(null);
        if (job == null || p == null || !TIMED.contains(job.getStatus())) return false;
        Instant now = clock.instant();
        Instant at = p.scheduledAt();
        boolean changed = switch (job.getStatus()) {
            case WORKING -> {
                PublishRoute route = job.getRouteIndex() < routes.size() ? routes.get(job.getRouteIndex()) : null;
                if (job.getTaskId() == null) {
                    advance(job, p, now);
                    yield true;
                }
                if (route != null && !now.isBefore(route.lastMoment(p))) {
                    if (isOfficial(job.getRoute())) {
                        p.flag(OFFICIAL_FLAG + "los enlaces públicos de las imágenes no llegaron a tiempo: se sigue por otra vía");
                        cleanup(p);
                    }
                    job.abandonTask("Claude no lo hizo a tiempo; se sigue por otra vía", now);
                    advance(job, p, now);
                    yield true;
                }
                yield false;
            }
            case RETRY_WAIT -> {
                if (job.getNextTryAt() == null || now.isBefore(job.getNextTryAt())) yield false;
                job.nextRound(now);
                advance(job, p, now);
                yield true;
            }
            case SCHEDULED, MANUAL -> {
                boolean manual = job.getStatus() == PublishJob.Status.MANUAL;
                if (now.isBefore(at)) yield false;
                if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
                if (now.isBefore(at.plus(manual ? CHECK_AFTER_MANUAL : CHECK_AFTER))) {
                    if (!job.getLog().getLast().contains("Publicando")) job.note(now, "Publicando: es la hora");
                    yield true;
                }
                check(job, p, now);
                yield true;
            }
            case CHECKING -> {
                if (now.isBefore(at.plus(CHECK_TIMEOUT))) {
                    // Las vías con API se vuelven a consultar cada pocos minutos; las de Claude esperan su resultado
                    if (job.getTaskId() != null || now.isBefore(job.getUpdatedAt().plus(RECHECK))) yield false;
                    check(job, p, now);
                    yield true;
                }
                fail(job, p, "No llegó la comprobación en " + CHECK_TIMEOUT.toHours() + " h", now);
                yield true;
            }
            default -> false;
        };
        if (changed) {
            publications.save(p);
            jobs.save(job);
            changed(publicationId);
        }
        return changed;
    }

    // ------------------------------------------------------------------ Por dentro

    /** Prueba las vías desde la actual. Si no queda ninguna, reintento o manual. */
    private void advance(PublishJob job, Publication p, Instant now) {
        Instant at = p.scheduledAt();
        while (job.getRouteIndex() < routes.size()) {
            PublishRoute route = routes.get(job.getRouteIndex());
            if (!route.applies(p)) {
                job.skipRoute();
                continue;
            }
            job.trying(job.getRouteIndex(), route.id().label(), now);
            if (!route.configured()) {
                job.routeFailed("no configurada", now);
                continue;
            }
            if (!now.isBefore(route.lastMoment(p))) {
                job.routeFailed("ya no da tiempo", now);
                continue;
            }
            PublishRoute.Outcome outcome;
            try {
                outcome = route.schedule(p, job, now);
            } catch (RuntimeException e) {
                outcome = new PublishRoute.Outcome.Failed(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            switch (outcome) {
                case PublishRoute.Outcome.Scheduled s -> {
                    job.scheduled(s.externalId(), s.url(), now);
                    return;
                }
                case PublishRoute.Outcome.Pending pending -> {
                    job.waitingFor(pending.taskId(), now);
                    job.note(now, route.id().label() + ": encargo a Claude " + pending.taskId());
                    return;
                }
                case PublishRoute.Outcome.Failed f -> {
                    if (route.id() == PublishRoute.Id.OFFICIAL) p.flag(OFFICIAL_FLAG + f.reason() + ": se sigue por otra vía");
                    job.routeFailed(f.reason(), now);
                }
                case PublishRoute.Outcome.Published pub -> job.routeFailed("respuesta inesperada al programar", now);
                case PublishRoute.Outcome.NotYet n -> job.routeFailed("respuesta inesperada al programar", now);
                case PublishRoute.Outcome.Doubt d -> job.routeFailed("respuesta inesperada al programar", now);
            }
        }
        Instant next = now.plus(RETRY);
        if (next.isBefore(at.minus(Publication.MIN_MARGIN))) {
            job.retryAt(next, now);
            return;
        }
        String leeme = writeManual(p, job, now);
        job.manual(leeme, now);
        // Si la API oficial falló, su motivo sigue a la vista junto al LEEME
        String official = p.getFlag() != null && p.getFlag().startsWith(OFFICIAL_FLAG) ? " · " + p.getFlag() : "";
        p.flag(PublishingService.MANUAL_FLAG + ": " + leeme + official);
    }

    private @Nullable PublishRoute route(@Nullable String label) {
        return routes.stream().filter(r -> r.id().label().equals(label)).findFirst().orElse(null);
    }

    private void check(PublishJob job, Publication p, Instant now) {
        PublishRoute route = route(job.getRoute());
        Optional<PublishRoute.Outcome> direct;
        try {
            direct = route == null ? Optional.empty() : route.check(p, job, now);
        } catch (RuntimeException e) {
            direct = Optional.of(new PublishRoute.Outcome.NotYet(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        if (direct.isPresent()) {
            switch (direct.get()) {
                case PublishRoute.Outcome.Published pub -> {
                    job.checking(null, now);
                    p.confirmPublished(pub.externalId(), pub.url(), now);
                    job.published(pub.externalId(), pub.url(), now);
                    cleanup(p);
                }
                case PublishRoute.Outcome.Pending pending -> job.checking(pending.taskId(), now);
                case PublishRoute.Outcome.Failed f -> {
                    job.checking(null, now);
                    fail(job, p, f.reason(), now);
                }
                case PublishRoute.Outcome.Scheduled s -> {
                    job.checking(null, now);
                    fail(job, p, "Sigue programada en " + job.getRoute() + " pasada la hora", now);
                }
                case PublishRoute.Outcome.NotYet n -> {
                    if (job.getStatus() != PublishJob.Status.CHECKING) job.checking(null, now);
                    job.touch(now);
                }
                case PublishRoute.Outcome.Doubt d -> {
                    if (job.getStatus() != PublishJob.Status.CHECKING) job.checking(null, now);
                    job.touch(now);
                }
            }
        } else {
            String task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLISH_CHECK,
                    "Comprobar publicación: " + p.getTitle(),
                    "Comprueba si la publicación «" + p.getTitle() + "» ya está publicada y deja el enlace o el identificador.",
                    List.of(p.getId()));
            job.checking(task, now);
        }
    }

    private void fail(PublishJob job, Publication p, String reason, Instant now) {
        cleanup(p);
        job.failed(reason, now);
        if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
        if (p.getStatus() == PublicationStatus.PUBLISHING) p.moveTo(PublicationStatus.PUBLISH_ERROR);
        p.flag(ERROR_FLAG + reason);
    }

    // ------------------------------------------------------------------ API oficial: publicar a la hora

    private enum Begin { NOT_DUE, HANDLED, STARTED }

    private static boolean isOfficial(@Nullable String routeLabel) { return PublishRoute.Id.OFFICIAL.label().equals(routeLabel); }

    private void cleanup(Publication p) {
        for (PublishRoute r : routes) {
            try {
                r.cleanup(p);
            } catch (RuntimeException e) {
                log.warn("Publicación {}: no se pudo limpiar lo que preparó {}: {}", p.getId(), r.id().label(), e.toString());
            }
        }
    }

    /**
     * La API oficial no programa: llegada la hora, SocialHub publica él mismo. En tres tiempos: (1) en su transacción,
     * deja la marca «publicando» (si después no hay resultado, hay duda y no se reintenta); (2) sin transacción, habla
     * con Meta, que puede tardar minutos; (3) aplica el resultado.
     *
     * @return {@code true} si ha hecho algo con esta publicación en este turno
     */
    private boolean publishOfficially(String publicationId) {
        PublishRoute[] route = new PublishRoute[1];
        Begin begin = transactions.execute(s -> beginOfficial(publicationId, route));
        if (begin == null || begin == Begin.NOT_DUE) return false;
        if (begin == Begin.HANDLED) return true;

        Publication p = publications.findById(publicationId).orElseThrow();
        PublishJob job = jobs.find(publicationId).orElseThrow();
        PublishRoute.Outcome outcome;
        try {
            outcome = route[0].publish(p, job, clock.instant());
        } catch (RuntimeException e) {
            outcome = new PublishRoute.Outcome.Doubt("error inesperado al publicar: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        PublishRoute.Outcome result = outcome;
        transactions.executeWithoutResult(s -> endOfficial(publicationId, result));
        return true;
    }

    private Begin beginOfficial(String publicationId, PublishRoute[] routeOut) {
        PublishJob job = jobs.find(publicationId).orElse(null);
        Publication p = publications.findById(publicationId).orElse(null);
        if (job == null || p == null || job.getStatus() != PublishJob.Status.SCHEDULED || !isOfficial(job.getRoute())) return Begin.NOT_DUE;
        PublishRoute route = route(job.getRoute());
        Instant now = clock.instant();
        Instant at = p.scheduledAt();
        if (route == null || now.isBefore(at)) return Begin.NOT_DUE;
        if (job.getPublishStartedAt() != null) {
            doubt(job, p, "SocialHub se cerró o se cortó mientras la publicaba por la API oficial: no se sabe si Instagram la publicó", now);
        } else if (now.isAfter(at.plus(OFFICIAL_GRACE))) {
            fallback(job, p, "SocialHub estaba cerrado a la hora y ya no se publica por la API oficial", now);
        } else {
            job.startPublishing(now);
            if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
            publications.save(p);
            jobs.save(job);
            changed(publicationId);
            routeOut[0] = route;
            return Begin.STARTED;
        }
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
        return Begin.HANDLED;
    }

    private void endOfficial(String publicationId, PublishRoute.Outcome outcome) {
        PublishJob job = jobs.find(publicationId).orElseThrow();
        Publication p = publications.findById(publicationId).orElseThrow();
        Instant now = clock.instant();
        switch (outcome) {
            case PublishRoute.Outcome.Published pub -> {
                cleanup(p);
                if (pub.url() == null) {
                    // Meta dio el identificador pero no el enlace: «Publicada» solo con la confirmación
                    job.awaitingConfirmation(pub.externalId() == null ? "" : pub.externalId(), now);
                } else {
                    p.confirmPublished(pub.externalId(), pub.url(), now);
                    job.published(pub.externalId(), pub.url(), now);
                }
            }
            case PublishRoute.Outcome.Failed f -> fallback(job, p, f.reason(), now);
            case PublishRoute.Outcome.Doubt d -> doubt(job, p, d.reason(), now);
            default -> doubt(job, p, "respuesta inesperada de la API oficial", now);
        }
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
    }

    /** Seguro que no se publicó: se sigue por la vía siguiente (Publer), con el motivo a la vista. */
    private void fallback(PublishJob job, Publication p, String reason, Instant now) {
        cleanup(p);
        p.flag(OFFICIAL_FLAG + reason + ": se sigue por otra vía");
        job.routeFailed(reason, now);
        advance(job, p, now);
    }

    /** No se sabe si se publicó: ni se reintenta ni se pasa a otra vía. */
    private void doubt(PublishJob job, Publication p, String reason, Instant now) {
        cleanup(p);
        job.failed(reason, now);
        if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
        if (p.getStatus() == PublicationStatus.PUBLISHING) p.moveTo(PublicationStatus.PUBLISH_ERROR);
        p.flag(DOUBT_FLAG + reason + ". Mira en Instagram si ya está publicada antes de volver a programarla");
    }

    /** LEEME para publicarla a mano, en la carpeta de aprobados, de generados o la primera que haya. */
    private String writeManual(Publication p, PublishJob job, Instant now) {
        var folders = promotions.folderMap(p.getPromotionId());
        String folder = folders.getOrDefault("APPROVED", folders.getOrDefault("GENERATED",
                folders.values().stream().findFirst().orElse(null)));
        StringBuilder text = new StringBuilder()
                .append("PUBLICAR A MANO · ").append(p.getTitle()).append("\n\n")
                .append("Ninguna vía pudo programarla con el margen de 6 h. Publícala tú a su hora.\n\n")
                .append("Red: ").append(p.getPlatform()).append(" · ").append(p.getType()).append('\n')
                .append("Cuándo: ").append(WHEN.format(p.scheduledAt())).append(" (hora de España)\n")
                .append("Publicación en SocialHub: ").append(p.getId()).append("\n\n")
                .append("Archivos, por orden:\n");
        files.files(p.getId()).forEach(f -> text.append("- ").append(f.path()).append('\n'));
        text.append("\nTexto:\n").append(p.getCaption() == null ? "(sin texto)" : p.getCaption()).append('\n');
        if (p.getHashtags() != null) text.append('\n').append(p.getHashtags()).append('\n');
        text.append("\nQué falló:\n");
        job.getLog().forEach(line -> text.append("- ").append(line).append('\n'));
        text.append("\nCuando esté publicada, SocialHub lo comprobará ")
                .append(CHECK_AFTER_MANUAL.toHours()).append(" h después de la hora.\n");
        if (folder == null) return "(sin carpeta de Drive: no se pudo escribir el LEEME)";
        try {
            Path dir = Path.of(folder);
            if (!Files.isDirectory(dir)) return "(no se encuentra la carpeta " + folder + ": no se pudo escribir el LEEME)";
            Path file = dir.resolve("LEEME-publicar-a-mano-" + p.getId() + ".txt");
            Files.writeString(file, text.toString());
            return file.toString();
        } catch (IOException | InvalidPathException e) {
            return "(no se pudo escribir el LEEME en " + folder + ": " + e.getMessage() + ")";
        }
    }

    private void changed(String publicationId) {
        events.publishEvent(new FactoryChanged("publication", publicationId));
    }
}
