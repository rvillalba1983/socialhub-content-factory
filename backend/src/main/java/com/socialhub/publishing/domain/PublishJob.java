package com.socialhub.publishing.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * Cómo va la publicación de una publicación programada. Prueba las vías por orden; si fallan todas, reintenta más
 * tarde mientras quede el margen; si no, queda para publicar a mano. Pasada la hora, espera la comprobación.
 */
@AggregateRoot
@Getter
public final class PublishJob {

    public enum Status {
        /** Probando una vía (puede estar esperando a Claude). */
        WORKING("Programando"),
        /** Han fallado todas las vías: se vuelve a probar a la hora indicada. */
        RETRY_WAIT("Reintento pendiente"),
        /** Programada en una vía: espera a la hora de publicación. */
        SCHEDULED("Programada"),
        /** Pasada la hora: espera la comprobación. */
        CHECKING("Comprobando"),
        /** Ninguna vía a tiempo: hay un LEEME para publicarla a mano (luego se comprueba igual). */
        MANUAL("Publicar a mano"),
        PUBLISHED("Publicada"),
        FAILED("Error de publicación"),
        CANCELLED("Cancelada");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }

        public boolean open() { return this != PUBLISHED && this != FAILED && this != CANCELLED; }
    }

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Madrid"));
    private static final int LOG_LINES = 60;

    @Identity
    private final String publicationId;
    private Status status;
    private int routeIndex;
    private @Nullable String route;
    private @Nullable String taskId;
    /** Encargos abandonados por llegar tarde: si aun así vuelven, puede haber un duplicado. */
    private final List<String> lateTaskIds;
    private @Nullable String externalId;
    private @Nullable String url;
    private @Nullable Instant nextTryAt;
    private int attempts;
    private @Nullable String lastError;
    private @Nullable String manualFile;
    private final List<String> log;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public PublishJob(String publicationId, Status status, int routeIndex, @Nullable String route, @Nullable String taskId,
                      List<String> lateTaskIds, @Nullable String externalId, @Nullable String url,
                      @Nullable Instant nextTryAt, int attempts, @Nullable String lastError, @Nullable String manualFile,
                      List<String> log, Instant createdAt, Instant updatedAt, long version) {
        this.publicationId = publicationId;
        this.status = status;
        this.routeIndex = routeIndex;
        this.route = route;
        this.taskId = taskId;
        this.lateTaskIds = new ArrayList<>(lateTaskIds);
        this.externalId = externalId;
        this.url = url;
        this.nextTryAt = nextTryAt;
        this.attempts = attempts;
        this.lastError = lastError;
        this.manualFile = manualFile;
        this.log = new ArrayList<>(log);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Una tarea nueva al pasar a «Programada». Sustituye a la anterior si la había. */
    public static PublishJob start(String publicationId, Instant now, long version) {
        PublishJob job = new PublishJob(publicationId, Status.WORKING, 0, null, null, List.of(), null, null, null, 1, null,
                null, List.of(), now, now, version);
        job.note(now, "Programada por Raúl: empieza la publicación");
        return job;
    }

    public void note(Instant now, String message) {
        log.add(LOG_TIME.format(now) + " · " + message);
        while (log.size() > LOG_LINES) log.removeFirst();
        updatedAt = now;
    }

    /** Se ha vuelto a mirar sin novedad. */
    public void touch(Instant now) { updatedAt = now; }

    /** Se está probando la vía {@code index}. */
    public void trying(int index, String routeLabel, Instant now) {
        requireOpen();
        status = Status.WORKING;
        routeIndex = index;
        route = routeLabel;
        taskId = null;
        nextTryAt = null;
        updatedAt = now;
    }

    /** La vía ha dejado un encargo a Claude y espera su resultado. */
    public void waitingFor(String task, Instant now) {
        requireOpen();
        taskId = task;
        updatedAt = now;
    }

    /** La vía no sirve esta vez: se anota y se pasa a la siguiente. */
    public void routeFailed(String reason, Instant now) {
        requireOpen();
        if (taskId != null) taskId = null;
        lastError = reason;
        note(now, (route == null ? "Vía" : route) + ": " + reason);
        routeIndex++;
        route = null;
    }

    /** Se deja de esperar el encargo en curso porque ya no llegaría a tiempo. */
    public void abandonTask(String reason, Instant now) {
        if (taskId != null) lateTaskIds.add(taskId);
        routeFailed(reason, now);
    }

    public boolean isLate(String task) { return lateTaskIds.contains(task); }

    public void scheduled(@Nullable String external, @Nullable String link, Instant now) {
        requireOpen();
        status = Status.SCHEDULED;
        taskId = null;
        externalId = blankToNull(external);
        url = blankToNull(link);
        lastError = null;
        note(now, "Programada en " + route + (externalId == null ? "" : " (" + externalId + ")"));
    }

    /** Han fallado todas las vías: se vuelve a empezar por la primera a esa hora. */
    public void retryAt(Instant when, Instant now) {
        requireOpen();
        status = Status.RETRY_WAIT;
        routeIndex = 0;
        route = null;
        taskId = null;
        nextTryAt = when;
        note(now, "Ninguna vía ha funcionado: se reintenta a las " + LOG_TIME.format(when));
    }

    public void nextRound(Instant now) {
        requireOpen();
        attempts++;
        status = Status.WORKING;
        nextTryAt = null;
        note(now, "Reintento " + attempts);
    }

    public void manual(String file, Instant now) {
        requireOpen();
        status = Status.MANUAL;
        route = "A mano";
        taskId = null;
        nextTryAt = null;
        manualFile = file;
        note(now, "Sin vía a tiempo: LEEME para publicarla a mano en " + file);
    }

    /** Pasada la hora: queda esperando la comprobación (el encargo, si lo hay, va en {@code task}). */
    public void checking(@Nullable String task, Instant now) {
        if (status != Status.SCHEDULED && status != Status.MANUAL && status != Status.CHECKING) {
            throw new IllegalStateException("Solo se comprueba una publicación programada");
        }
        status = Status.CHECKING;
        taskId = task;
        note(now, "Hora de publicación: se comprueba" + (route == null ? "" : " (" + route + ")"));
    }

    public void published(@Nullable String external, @Nullable String link, Instant now) {
        status = Status.PUBLISHED;
        taskId = null;
        if (blankToNull(external) != null) externalId = blankToNull(external);
        if (blankToNull(link) != null) url = blankToNull(link);
        note(now, "Publicada" + (url == null ? "" : ": " + url));
    }

    public void failed(String reason, Instant now) {
        status = Status.FAILED;
        if (taskId != null) lateTaskIds.add(taskId);
        taskId = null;
        lastError = reason;
        note(now, "Error de publicación: " + reason);
    }

    public void cancel(String reason, Instant now) {
        if (!status.open()) return;
        if (taskId != null) lateTaskIds.add(taskId);
        status = Status.CANCELLED;
        taskId = null;
        nextTryAt = null;
        note(now, reason);
    }

    private void requireOpen() {
        if (!status.open()) throw new IllegalStateException("La publicación ya no está en marcha");
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    public static List<String> split(String text) {
        return text.isBlank() ? List.of() : Arrays.asList(text.split("\n"));
    }
}
