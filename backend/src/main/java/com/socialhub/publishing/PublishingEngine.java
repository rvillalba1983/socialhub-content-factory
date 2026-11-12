package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.ClaudePasses;
import com.socialhub.ai.ClaudePickups;
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
 * Motor de publicación (fase 7). Al pasar una publicación a «Programada» prueba las vías por orden: API oficial (solo
 * Instagram, cuando está configurada), Publer y Publora. Si fallan todas, reintenta cada hora mientras quede el margen
 * de 6 h; después deja un LEEME para publicarla a mano y avisa. Pasada la hora, la pasa a «Publicando» y pide la
 * comprobación: «Publicada» solo llega con el enlace o el identificador; si no aparece, «Error de publicación».
 * <p>
 * La API oficial no programa: SocialHub publica él mismo. En el corte previo (cuando Publer aún llega) comprueba todo y,
 * si algo falla, cede a Publer con el motivo. Pasado el corte, un fallo a la hora ya no tiene otra vía: aviso con LEEME.
 * Si SocialHub estaba cerrado a la hora, publica al abrirse sin límite de tiempo y avisa del retraso. Ante la duda de si
 * Meta publicó (o con identificador sin enlace), no reintenta ni cambia de vía: queda para revisar.
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
    static final String HELD_FLAG = "Retenida: ";
    /** Cada cuánto se vuelve a probar una publicación retenida (se habrá indicado la cuenta o habrá salido alguna). */
    static final Duration HELD_RETRY = Duration.ofMinutes(5);
    /** La API oficial no pudo y se sigue por otra vía (el motivo queda visible). */
    static final String OFFICIAL_FLAG = "API oficial: ";
    /** No se sabe si Instagram llegó a publicarla: no se reintenta, se revisa. */
    static final String DOUBT_FLAG = "Revisar antes de reintentar: ";
    /** Publicada por la API oficial con retraso (SocialHub estaba cerrado a la hora): se avisa de cuánto. */
    static final String DELAY_FLAG = "Publicada con ";
    /** Retraso a partir del cual se avisa (la ronda del motor es cada minuto y una subida de vídeo tarda). */
    static final Duration DELAY_NOTICE = Duration.ofMinutes(10);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));
    private static final EnumSet<PublishJob.Status> TIMED = EnumSet.of(PublishJob.Status.WORKING,
            PublishJob.Status.HELD, PublishJob.Status.RETRY_WAIT, PublishJob.Status.SCHEDULED, PublishJob.Status.MANUAL, PublishJob.Status.CHECKING);

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
            if (p.getFlag() != null && p.getFlag().startsWith(HELD_FLAG)) p.flag(null);
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

    /**
     * Raúl manda ya a otra vía una publicación retenida (sin esperar a que Publer deje de llegar). Sigue el flujo normal
     * de esa vía.
     *
     * @return {@code false} si no estaba retenida o la vía no existe
     */
    boolean moveHeld(String publicationId, PublishRoute.Id target) {
        PublishJob job = jobs.find(publicationId).orElse(null);
        Publication p = publications.findById(publicationId).orElse(null);
        if (job == null || p == null || job.getStatus() != PublishJob.Status.HELD) return false;
        int index = -1;
        for (int i = 0; i < routes.size(); i++) if (routes.get(i).id() == target) index = i;
        if (index < 0) return false;
        job.skipTo(index, target.label(), clock.instant());
        advance(job, p, clock.instant());
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
        return true;
    }

    /** Incidencias que anota el motor: al volver a programarla se quitan. */
    static boolean ownFlag(@Nullable String flag) {
        return flag != null && (flag.startsWith(LATE_FLAG) || flag.startsWith(STILL_SCHEDULED)
                || flag.startsWith(ERROR_FLAG) || flag.startsWith(HELD_FLAG) || flag.startsWith(OFFICIAL_FLAG)
                || flag.startsWith(DOUBT_FLAG) || flag.startsWith(DELAY_FLAG)
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
        // La hora de la vuelta se fija al empezar: una subida larga a Meta no debe cambiar lo que toca a las demás
        Instant round = clock.instant();
        int changed = 0;
        for (PublishJob listed : jobs.withStatus(TIMED)) {
            try {
                if (listed.getStatus() == PublishJob.Status.SCHEDULED && isOfficial(listed.getRoute())
                        && officialStep(listed.getPublicationId(), round)) {
                    changed++;
                    continue;
                }
                Boolean done = transactions.execute(s -> step(listed.getPublicationId(), round));
                if (Boolean.TRUE.equals(done)) changed++;
            } catch (RuntimeException e) {
                log.warn("Publicación {}: {}", listed.getPublicationId(), e.toString());
            }
        }
        return changed;
    }

    private boolean step(String publicationId, Instant now) {
        PublishJob job = jobs.find(publicationId).orElse(null);
        Publication p = publications.findById(publicationId).orElse(null);
        if (job == null || p == null || !TIMED.contains(job.getStatus())) return false;
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
                // El encargo de enlaces va con retraso (su recogida ya pasó): no se espera al corte, se sigue por Publer
                if (route != null && isOfficial(job.getRoute())
                        && ClaudePickups.overdue(job.getUpdatedAt(), now,
                                ClaudePasses.urgent(false, route.lastMoment(p), job.getUpdatedAt()))) {
                    p.flag(OFFICIAL_FLAG + "Claude no ha recogido el encargo de los enlaces públicos: se sigue por otra vía");
                    cleanup(p);
                    job.abandonTask("el encargo de los enlaces públicos va con retraso", now);
                    advance(job, p, now);
                    yield true;
                }
                yield false;
            }
            case HELD -> {
                if (job.getNextTryAt() == null || now.isBefore(job.getNextTryAt())) yield false;
                String before = job.getLastError();
                job.resume(now);
                advance(job, p, now);
                // Sigue retenida por lo mismo: no se guarda ni se avisa a la interfaz (se vuelve a mirar en el siguiente turno)
                yield !(job.getStatus() == PublishJob.Status.HELD && java.util.Objects.equals(before, job.getLastError()));
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
                // Las 24 h cuentan desde que se publicó de verdad (si SocialHub la publicó con retraso), no desde la hora prevista
                Instant since = job.getPublishStartedAt() != null && job.getPublishStartedAt().isAfter(at) ? job.getPublishStartedAt() : at;
                if (now.isBefore(since.plus(CHECK_TIMEOUT))) {
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
        if (p.getFlag() != null && p.getFlag().startsWith(HELD_FLAG)) p.flag(null);
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
                    // Programada ya pasado el corte previo: lo que se acaba de comprobar vale como esa comprobación
                    if (route.publishesAtTime() && !now.isBefore(route.cutoff(p))) job.preflightDone(now);
                    return;
                }
                case PublishRoute.Outcome.Pending pending -> {
                    job.waitingFor(pending.taskId(), now);
                    job.note(now, route.id().label() + ": encargo a Claude " + pending.taskId());
                    return;
                }
                case PublishRoute.Outcome.Held h -> {
                    job.hold(h.reason(), now.plus(HELD_RETRY), now);
                    // No pisa otra incidencia de la publicación: entonces el motivo queda solo en la tarea
                    if (p.getFlag() == null) p.flag(HELD_FLAG + h.reason());
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
        String leeme = writeManual(p, job, now, null);
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
                case PublishRoute.Outcome.Held h -> {
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
        // Si Meta ya dio el identificador, la publicación existe: nunca es un simple error, se revisa en Instagram
        if (isOfficial(job.getRoute()) && job.getExternalId() != null) {
            doubt(job, p, "Meta la publicó con el identificador " + job.getExternalId() + " pero no se pudo confirmar (" + reason + ")", now);
            return;
        }
        cleanup(p);
        job.failed(reason, now);
        if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
        if (p.getStatus() == PublicationStatus.PUBLISHING) p.moveTo(PublicationStatus.PUBLISH_ERROR);
        p.flag(ERROR_FLAG + reason);
    }

    // ------------------------------------------------------------------ API oficial: publicar a la hora

    private enum Begin { NOT_DUE, HANDLED, PREFLIGHT, STARTED }

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
     * La API oficial no programa: SocialHub publica él mismo. En el corte previo (cuando Publer aún llega) comprueba todo;
     * a la hora, en tres tiempos: (1) en su transacción, deja la marca «publicando» (si después no hay resultado, hay duda
     * y no se reintenta); (2) sin transacción, habla con Meta, que puede tardar minutos; (3) aplica el resultado. Si
     * SocialHub estaba cerrado a la hora, publica en cuanto se abre, sin límite de tiempo, y avisa del retraso.
     *
     * @return {@code true} si ha hecho algo con esta publicación en este turno
     */
    private boolean officialStep(String publicationId, Instant round) {
        PublishRoute[] route = new PublishRoute[1];
        Begin begin = transactions.execute(s -> beginOfficial(publicationId, round, route));
        if (begin == null || begin == Begin.NOT_DUE) return false;
        if (begin == Begin.HANDLED) return true;

        Publication p = publications.findById(publicationId).orElseThrow();
        PublishJob job = jobs.find(publicationId).orElseThrow();
        if (begin == Begin.PREFLIGHT) {
            PublishRoute.Outcome outcome;
            try {
                outcome = route[0].preflight(p, job, round);
            } catch (RuntimeException e) {
                outcome = new PublishRoute.Outcome.Failed(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            PublishRoute.Outcome result = outcome;
            transactions.executeWithoutResult(s -> endPreflight(publicationId, result));
            return true;
        }
        PublishRoute.Outcome outcome;
        try {
            outcome = route[0].publish(p, job, round);
        } catch (RuntimeException e) {
            outcome = new PublishRoute.Outcome.Doubt("error inesperado al publicar: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        PublishRoute.Outcome result = outcome;
        transactions.executeWithoutResult(s -> endOfficial(publicationId, result));
        return true;
    }

    private Begin beginOfficial(String publicationId, Instant round, PublishRoute[] routeOut) {
        PublishJob job = jobs.find(publicationId).orElse(null);
        Publication p = publications.findById(publicationId).orElse(null);
        if (job == null || p == null || job.getStatus() != PublishJob.Status.SCHEDULED || !isOfficial(job.getRoute())) return Begin.NOT_DUE;
        PublishRoute route = route(job.getRoute());
        Instant at = p.scheduledAt();
        if (route == null) return Begin.NOT_DUE;
        if (round.isBefore(at)) {
            // El corte previo: aún no es la hora, pero Publer ya está a punto de dejar de llegar. Se comprueba todo ahora
            if (job.getPreflightAt() == null && !round.isBefore(route.cutoff(p))) {
                routeOut[0] = route;
                return Begin.PREFLIGHT;
            }
            return Begin.NOT_DUE;
        }
        Instant now = clock.instant();
        if (job.getPublishStartedAt() != null) {
            doubt(job, p, "SocialHub se cerró o se cortó mientras la publicaba por la API oficial: no se sabe si Instagram la publicó", now);
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

    /** Resultado de las comprobaciones del corte previo: todo bien, o se cede a Publer con el motivo. */
    private void endPreflight(String publicationId, PublishRoute.Outcome outcome) {
        PublishJob job = jobs.find(publicationId).orElseThrow();
        Publication p = publications.findById(publicationId).orElseThrow();
        if (job.getStatus() != PublishJob.Status.SCHEDULED || !isOfficial(job.getRoute())) return;
        Instant now = clock.instant();
        if (outcome instanceof PublishRoute.Outcome.Scheduled) {
            job.preflightDone(now);
        } else {
            String reason = outcome instanceof PublishRoute.Outcome.Failed f ? f.reason() : "comprobación no válida";
            fallback(job, p, reason, now);
        }
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
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
                noteDelay(job, p);
            }
            case PublishRoute.Outcome.Failed f -> failedAtTime(job, p, f.reason(), now);
            case PublishRoute.Outcome.Doubt d -> doubt(job, p, d.reason(), now);
            default -> doubt(job, p, "respuesta inesperada de la API oficial", now);
        }
        publications.save(p);
        jobs.save(job);
        changed(publicationId);
    }

    /** Si empezó a publicar mucho después de su hora (SocialHub estaba cerrado), se avisa de cuánto. */
    private void noteDelay(PublishJob job, Publication p) {
        Instant started = job.getPublishStartedAt();
        if (started == null) return;
        Duration late = Duration.between(p.scheduledAt(), started);
        if (late.compareTo(DELAY_NOTICE) < 0) return;
        p.flag(DELAY_FLAG + human(late) + " de retraso: SocialHub no estaba abierto a su hora");
    }

    static String human(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** Antes de publicar, Publer aún llegaba: se sigue por la vía siguiente (Publer), con el motivo a la vista. */
    private void fallback(PublishJob job, Publication p, String reason, Instant now) {
        cleanup(p);
        p.flag(OFFICIAL_FLAG + reason + ": se sigue por otra vía");
        job.routeFailed(reason, now);
        advance(job, p, now);
    }

    /**
     * Falló a la hora, pero seguro que no se publicó. Ya no hay vía que llegue (Publer necesita 8 h): aviso claro y LEEME
     * para publicarla a mano o cambiarle la fecha.
     */
    private void failedAtTime(PublishJob job, Publication p, String reason, Instant now) {
        cleanup(p);
        String leeme = writeManual(p, job, now, reason);
        String text = reason + ". La hora ya pasó: publícala tú cuanto antes o cámbiale la fecha (LEEME: " + leeme + ")";
        job.failed(text, now);
        if (p.getStatus() == PublicationStatus.SCHEDULED) p.moveTo(PublicationStatus.PUBLISHING);
        if (p.getStatus() == PublicationStatus.PUBLISHING) p.moveTo(PublicationStatus.PUBLISH_ERROR);
        p.flag(OFFICIAL_FLAG + text);
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
    private String writeManual(Publication p, PublishJob job, Instant now, @Nullable String lateReason) {
        var folders = promotions.folderMap(p.getPromotionId());
        String folder = folders.getOrDefault("APPROVED", folders.getOrDefault("GENERATED",
                folders.values().stream().findFirst().orElse(null)));
        StringBuilder text = new StringBuilder()
                .append("PUBLICAR A MANO · ").append(p.getTitle()).append("\n\n")
                .append(lateReason == null
                        ? "Ninguna vía pudo programarla con el margen de 6 h. Publícala tú a su hora.\n\n"
                        : "La API oficial de Instagram no pudo publicarla: " + lateReason + ".\nLa hora de publicación ya pasó: "
                                + "publícala tú cuanto antes, o cámbiale la fecha en SocialHub y vuelve a programarla.\n\n")
                .append("Red: ").append(p.getPlatform()).append(" · ").append(p.getType()).append('\n')
                .append("Cuándo: ").append(WHEN.format(p.scheduledAt())).append(" (hora de España)\n")
                .append("Publicación en SocialHub: ").append(p.getId()).append("\n\n")
                .append("Archivos, por orden:\n");
        files.files(p.getId()).forEach(f -> text.append("- ").append(f.path()).append('\n'));
        text.append("\nTexto:\n").append(p.getCaption() == null ? "(sin texto)" : p.getCaption()).append('\n');
        if (p.getHashtags() != null) text.append('\n').append(p.getHashtags()).append('\n');
        text.append("\nQué falló:\n");
        job.getLog().forEach(line -> text.append("- ").append(line).append('\n'));
        if (lateReason == null) {
            text.append("\nCuando esté publicada, SocialHub lo comprobará ")
                    .append(CHECK_AFTER_MANUAL.toHours()).append(" h después de la hora.\n");
        }
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
