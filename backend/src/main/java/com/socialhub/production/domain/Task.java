package com.socialhub.production.domain;

import static com.socialhub.production.domain.TaskStatus.*;

import com.socialhub.platform.DomainException;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.production.ProductionEvents.TaskCompleted;
import com.socialhub.production.ProductionEvents.TaskInterrupted;
import com.socialhub.production.ProductionEvents.TaskNotice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * Una tarea de la fábrica con su máquina de estados.
 *
 * <p>Todas las órdenes pasan por {@link #execute}, que rechaza las que no están permitidas en el estado actual.
 * Los cambios dejan líneas en el historial ({@link #newHistory()}) y, cuando afectan a otros módulos,
 * eventos de dominio ({@link #pendingNotices()}).
 */
@AggregateRoot
@Getter
public final class Task {

    public static final String TYPE_REVIEW = "REVIEW";

    @Identity
    private final String id;
    private final String stationId;
    private final @Nullable String promotionId;
    private final String type;
    private final String title;
    private final String goal;
    private final @Nullable String why;
    private final List<String> inputs;
    private TaskStatus status;
    private boolean paused;
    private double progress;
    private Agent agent;
    private @Nullable String reason;
    private @Nullable String error;
    private @Nullable String result;
    private boolean human;
    private int attempts;
    private final int maxAttempts;
    private @Nullable Instant startedAt;
    private final List<TaskStep> steps;
    private final List<TaskCheck> checks;
    private final Set<String> dependsOn;
    private final List<TaskHistoryEntry> history;
    private final boolean demo;
    private final long version;

    @Getter(AccessLevel.NONE)
    private final List<TaskHistoryEntry> newHistory = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    private final List<TaskNotice> pendingNotices = new ArrayList<>();

    @Builder
    public Task(String id, String stationId, @Nullable String promotionId, @Nullable String type, String title,
                @Nullable String goal, @Nullable String why, @Nullable List<String> inputs, TaskStatus status,
                boolean paused, double progress, Agent agent, @Nullable String reason, @Nullable String error,
                @Nullable String result, boolean human, int attempts, int maxAttempts, @Nullable Instant startedAt,
                @Nullable List<TaskStep> steps, @Nullable List<TaskCheck> checks, @Nullable Set<String> dependsOn,
                @Nullable List<TaskHistoryEntry> history, boolean demo, long version) {
        this.id = Objects.requireNonNull(id);
        this.stationId = Objects.requireNonNull(stationId);
        this.promotionId = promotionId;
        this.type = type == null ? "GENERIC" : type;
        this.title = Objects.requireNonNull(title);
        this.goal = goal == null ? "" : goal;
        this.why = why;
        this.inputs = inputs == null ? List.of() : List.copyOf(inputs);
        this.status = Objects.requireNonNull(status);
        this.paused = paused;
        this.progress = progress;
        this.agent = Objects.requireNonNull(agent);
        this.reason = reason;
        this.error = error;
        this.result = result;
        this.human = human;
        this.attempts = Math.max(1, attempts);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.startedAt = startedAt;
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
        this.dependsOn = dependsOn == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(dependsOn));
        this.history = history == null ? new ArrayList<>() : new ArrayList<>(history);
        this.demo = demo;
        this.version = version;
        if (paused && status != WAITING) {
            throw new IllegalArgumentException("Solo una tarea en espera puede estar pausada");
        }
    }

    /** Órdenes que la interfaz puede ofrecer en el estado actual. */
    public Set<TaskCommandType> allowedCommands() {
        Set<TaskCommandType> commands = EnumSet.noneOf(TaskCommandType.class);
        switch (status) {
            case RUNNING -> commands.addAll(List.of(TaskCommandType.PAUSE, TaskCommandType.CANCEL));
            case QUEUED -> commands.addAll(List.of(TaskCommandType.RUN_NOW, TaskCommandType.CANCEL));
            case WAITING -> {
                if (paused) {
                    commands.add(TaskCommandType.RESUME);
                } else if (human && TYPE_REVIEW.equals(type)) {
                    commands.addAll(List.of(TaskCommandType.APPROVE, TaskCommandType.REMIND));
                } else if (human) {
                    commands.addAll(List.of(TaskCommandType.REMIND, TaskCommandType.MARK_DONE));
                } else {
                    commands.addAll(List.of(TaskCommandType.RUN_NOW, TaskCommandType.RESCHEDULE));
                }
                commands.add(TaskCommandType.CANCEL);
            }
            case BLOCKED -> commands.addAll(List.of(TaskCommandType.RECHECK, TaskCommandType.CANCEL));
            case FAILED -> {
                if (attempts < maxAttempts) commands.add(TaskCommandType.RETRY);
                commands.add(TaskCommandType.SWITCH_TO_MANUAL);
                commands.add(TaskCommandType.CANCEL);
            }
            case COMPLETED -> commands.add(TaskCommandType.RERUN);
            case IDLE -> commands.addAll(List.of(TaskCommandType.START, TaskCommandType.DISCARD));
            case CANCELLED -> commands.add(TaskCommandType.REOPEN);
        }
        if (agent.isAi() && status != COMPLETED && status != CANCELLED) {
            commands.add(TaskCommandType.CHANGE_AGENT);
        }
        return commands;
    }

    public List<AvailableCommand> availableCommands() {
        return allowedCommands().stream().map(AvailableCommand::of).toList();
    }

    /**
     * Ejecuta una orden.
     *
     * @param model modelo de IA para {@link TaskCommandType#CHANGE_AGENT}; obligatorio solo en esa orden
     * @throws DomainException si la orden no está permitida en el estado actual
     */
    public void execute(TaskCommandType command, @Nullable AiModel model, Instant now) {
        if (!allowedCommands().contains(command)) {
            throw new DomainException("No se puede «" + command.label() + "» una tarea en estado «"
                    + (paused ? "En espera · pausada" : status.label()) + "».");
        }
        switch (command) {
            case PAUSE -> {
                pendingNotices.add(new TaskInterrupted(id));
                status = WAITING;
                paused = true;
                reason = "Pausada por ti.";
                record(now, "Pausada");
            }
            case RESUME -> {
                // Vuelve a la cola: el motor (o el simulador) la retoma cuando la estación acepte trabajo
                status = QUEUED;
                paused = false;
                reason = "Reanudada: en cola.";
                record(now, "Reanudada");
            }
            case CANCEL, DISCARD -> {
                if (status == RUNNING) pendingNotices.add(new TaskInterrupted(id));
                status = CANCELLED;
                paused = false;
                reason = command == TaskCommandType.DISCARD ? "Descartada por ti." : "Cancelada por ti.";
                record(now, command == TaskCommandType.DISCARD ? "Descartada" : "Cancelada");
            }
            case RETRY -> {
                attempts++;
                status = QUEUED;
                error = null;
                reason = "Reintento " + attempts + " de " + maxAttempts + ": en cola.";
                steps.replaceAll(s -> s.state() == StepState.FAILED ? s.with(StepState.PENDING) : s);
                record(now, "Reintento " + attempts + " de " + maxAttempts);
            }
            case SWITCH_TO_MANUAL -> {
                status = WAITING;
                human = true;
                agent = Agent.raul();
                error = null;
                reason = "Publicación manual: LEEME preparado en la carpeta de la campaña. Raúl publica.";
                record(now, "Pasada a manual con LEEME");
                pendingNotices.add(new SwitchedToManual(id, promotionId));
            }
            case RUN_NOW -> {
                startRunning(now, Math.max(progress, 3));
                record(now, "Ejecución adelantada");
            }
            case START -> {
                status = QUEUED;
                reason = "Aprobada: en cola.";
                record(now, "Aprobada e iniciada");
            }
            case RERUN -> {
                status = QUEUED;
                progress = 0;
                result = null;
                reason = "Relanzada: en cola.";
                steps.replaceAll(s -> s.with(StepState.PENDING));
                record(now, "Ejecución relanzada");
            }
            case REOPEN -> {
                status = QUEUED;
                reason = "Reactivada: en cola.";
                record(now, "Reactivada");
            }
            case APPROVE -> {
                checks.replaceAll(c -> new TaskCheck(c.label(), true));
                complete("Aprobada. Pasa a programación.");
                record(now, "Aprobada por Raúl");
                pendingNotices.add(new ReviewApproved(id, promotionId));
            }
            case MARK_DONE -> {
                complete("Hecho por Raúl.");
                record(now, "Marcada como hecha");
            }
            case RESCHEDULE -> record(now, "Reprogramada un día después");
            case RECHECK -> record(now, "Comprobado: la dependencia sigue pendiente");
            case REMIND -> record(now, "Aviso preparado para Raúl");
            case CHANGE_AGENT -> {
                if (model == null) throw new IllegalArgumentException("Falta el modelo para cambiar de agente");
                Agent next = Agent.ai(model);
                record(now, "Agente cambiado de " + agent.label() + " a " + next.label());
                agent = next;
            }
        }
    }

    /** Las dependencias ya están completadas: la tarea bloqueada pasa a la cola. */
    public boolean unblockIfReady(Set<String> completedTaskIds, Instant now) {
        if (status != BLOCKED || dependsOn.isEmpty() || !completedTaskIds.containsAll(dependsOn)) {
            return false;
        }
        status = QUEUED;
        reason = "Dependencia resuelta: en cola.";
        record(now, "Dependencia resuelta");
        return true;
    }

    /** El motor (o el simulador del modo demo) empieza a trabajar en una tarea de la cola. */
    public void startFromQueue(Instant now) {
        if (status != QUEUED) throw new DomainException("Solo se puede arrancar una tarea en cola");
        startRunning(now, progress >= 100 ? 0 : progress);
        record(now, "En ejecución");
    }

    /** El motor termina la ejecución con éxito. */
    public void finishRun(String resultText, Instant now) {
        if (status != RUNNING) throw new DomainException("Solo termina una tarea en progreso");
        complete(resultText);
        record(now, "Completada");
    }

    /** El motor termina la ejecución con error. Raúl puede reintentar mientras queden intentos. */
    public void failRun(String errorText, Instant now) {
        if (status != RUNNING) throw new DomainException("Solo falla una tarea en progreso");
        status = FAILED;
        error = errorText;
        reason = null;
        steps.replaceAll(s -> s.state() == StepState.IN_PROGRESS ? s.with(StepState.FAILED) : s);
        record(now, "Error: " + errorText);
    }

    /** Progreso informado por el motor (0–100). No completa la tarea: eso lo hace {@link #finishRun}. */
    public void reportProgress(double value) {
        if (status == RUNNING) progress = Math.max(0, Math.min(99, value));
    }

    /** Línea del registro de ejecución. */
    public void log(String message, Instant now) {
        record(now, message);
    }

    /** Vuelve a poner en cola una tarea que no está en marcha (comprobaciones que se repiten). */
    public boolean requeue(Instant now) {
        if (status == RUNNING || status == QUEUED) return false;
        status = QUEUED;
        paused = false;
        progress = 0;
        error = null;
        result = null;
        reason = "En cola.";
        attempts = 1;
        steps.replaceAll(s -> s.with(StepState.PENDING));
        record(now, "En cola de nuevo");
        return true;
    }

    /**
     * SocialHub se cerró con la tarea en ejecución: vuelve a la cola sin gastar un intento, porque el fallo no
     * fue suyo.
     */
    public boolean recoverAfterRestart(Instant now) {
        if (status != RUNNING) return false;
        status = QUEUED;
        reason = "Recuperada tras reiniciar SocialHub: en cola.";
        record(now, "Recuperada tras reiniciar SocialHub");
        return true;
    }

    /** Avance del trabajo en curso. Al llegar al 100 % la tarea se completa. */
    public void advance(double delta, Instant now) {
        if (status != RUNNING) throw new DomainException("Solo avanza una tarea en progreso");
        progress = Math.min(100, progress + Math.max(0, delta));
        if (!steps.isEmpty()) {
            double share = 100.0 / steps.size();
            int doneSteps = (int) Math.floor(progress / share);
            for (int i = 0; i < steps.size(); i++) {
                StepState target = i < doneSteps ? StepState.DONE : i == doneSteps ? StepState.IN_PROGRESS : StepState.PENDING;
                if (rank(steps.get(i).state()) < rank(target)) steps.set(i, steps.get(i).with(target));
            }
        }
        if (progress >= 100) {
            complete(result != null ? result : "Completada.");
            record(now, "Completada");
        }
    }

    private static int rank(StepState state) {
        return switch (state) {
            case FAILED, PENDING -> 0;
            case IN_PROGRESS -> 1;
            case DONE -> 2;
        };
    }

    private void startRunning(Instant now, double fromProgress) {
        status = RUNNING;
        paused = false;
        reason = null;
        progress = fromProgress;
        startedAt = now;
        if (steps.stream().noneMatch(s -> s.state() == StepState.IN_PROGRESS)) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).state() != StepState.DONE) {
                    steps.set(i, steps.get(i).with(StepState.IN_PROGRESS));
                    break;
                }
            }
        }
    }

    private void complete(String resultText) {
        status = COMPLETED;
        paused = false;
        progress = 100;
        reason = null;
        result = resultText;
        steps.replaceAll(s -> s.with(StepState.DONE));
        pendingNotices.add(new TaskCompleted(id));
    }

    private void record(Instant at, String message) {
        TaskHistoryEntry entry = new TaskHistoryEntry(at, message);
        history.add(entry);
        newHistory.add(entry);
    }

    public List<TaskStep> getSteps() { return Collections.unmodifiableList(steps); }
    public List<TaskCheck> getChecks() { return Collections.unmodifiableList(checks); }
    public List<TaskHistoryEntry> getHistory() { return Collections.unmodifiableList(history); }

    /** Líneas del historial añadidas desde que se cargó la tarea (se guardan solo estas). */
    public List<TaskHistoryEntry> newHistory() { return Collections.unmodifiableList(newHistory); }

    public List<TaskNotice> pendingNotices() { return Collections.unmodifiableList(pendingNotices); }

    /** Tras guardar: los eventos ya se han publicado y el historial nuevo ya está en la base de datos. */
    public void clearPending() {
        newHistory.clear();
        pendingNotices.clear();
    }
}
