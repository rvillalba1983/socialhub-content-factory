package com.socialhub.production.domain;

import static com.socialhub.production.domain.TaskStatus.*;

import com.socialhub.platform.DomainException;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.production.ProductionEvents.TaskCompleted;
import com.socialhub.production.ProductionEvents.TaskNotice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Una tarea de la fábrica con su máquina de estados.
 *
 * <p>Todas las transiciones pasan por {@link #apply}, que rechaza las que no están permitidas en el
 * estado actual. Los cambios dejan eventos en el historial ({@link #pendingEvents()}) y, cuando
 * afectan a otros módulos, avisos de dominio ({@link #pendingNotices()}).
 */
public final class Task {

    public static final String TYPE_REVIEW = "review";

    private final String id;
    private final String stationId;
    private final String promotionId;
    private final String type;
    private final String title;
    private TaskStatus status;
    private boolean paused;
    private double progress;
    private Agent agent;
    private String reason;
    private String error;
    private String result;
    private boolean human;
    private int attempts;
    private final int maxAttempts;
    private Instant startedAt;
    private final List<Step> steps;
    private final List<Check> checks;
    private final List<String> dependsOn;
    private final int version;

    private final List<String> pendingEvents = new ArrayList<>();
    private final List<TaskNotice> pendingNotices = new ArrayList<>();

    public record Step(String label, double state) {
        public boolean done() { return state >= 1; }
        public boolean failed() { return state < 0; }
    }

    public record Check(String label, boolean passed) {}

    public Task(String id, String stationId, String promotionId, String type, String title, TaskStatus status,
                boolean paused, double progress, Agent agent, String reason, String error, String result,
                boolean human, int attempts, int maxAttempts, Instant startedAt, List<Step> steps,
                List<Check> checks, List<String> dependsOn, int version) {
        this.id = Objects.requireNonNull(id);
        this.stationId = Objects.requireNonNull(stationId);
        this.promotionId = promotionId;
        this.type = type == null ? "generic" : type;
        this.title = Objects.requireNonNull(title);
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
        this.steps = new ArrayList<>(steps);
        this.checks = new ArrayList<>(checks);
        this.dependsOn = List.copyOf(dependsOn);
        this.version = version;
        if (paused && status != WAITING) {
            throw new IllegalArgumentException("Solo una tarea en espera puede estar pausada");
        }
    }

    /** Acciones que la interfaz puede ofrecer en el estado actual. */
    public Set<TaskAction> allowedActions() {
        Set<TaskAction> actions = EnumSet.noneOf(TaskAction.class);
        switch (status) {
            case RUNNING -> actions.addAll(List.of(TaskAction.PAUSE, TaskAction.CANCEL));
            case QUEUED -> actions.addAll(List.of(TaskAction.RUN_NOW, TaskAction.CANCEL));
            case WAITING -> {
                if (paused) {
                    actions.add(TaskAction.RESUME);
                } else if (human && TYPE_REVIEW.equals(type)) {
                    actions.addAll(List.of(TaskAction.APPROVE, TaskAction.REMIND));
                } else if (human) {
                    actions.addAll(List.of(TaskAction.REMIND, TaskAction.MARK_DONE));
                } else {
                    actions.addAll(List.of(TaskAction.RUN_NOW, TaskAction.RESCHEDULE));
                }
                actions.add(TaskAction.CANCEL);
            }
            case BLOCKED -> actions.addAll(List.of(TaskAction.RECHECK, TaskAction.CANCEL));
            case ERROR -> {
                if (attempts < maxAttempts) actions.add(TaskAction.RETRY);
                actions.add(TaskAction.SWITCH_TO_MANUAL);
                actions.add(TaskAction.CANCEL);
            }
            case COMPLETED -> actions.add(TaskAction.RERUN);
            case IDLE -> actions.addAll(List.of(TaskAction.START, TaskAction.DISCARD));
            case CANCELLED -> actions.add(TaskAction.REOPEN);
        }
        if (agent.isAi() && status != COMPLETED && status != CANCELLED) {
            actions.add(TaskAction.CHANGE_AGENT);
        }
        return actions;
    }

    /**
     * Aplica una acción.
     *
     * @param newModel modelo de IA para {@link TaskAction#CHANGE_AGENT}; se ignora en las demás
     * @throws DomainException si la acción no está permitida en el estado actual
     */
    public void apply(TaskAction action, String newModel, Instant now) {
        if (!allowedActions().contains(action)) {
            throw new DomainException("No se puede «" + action.label() + "» una tarea en estado «"
                    + (paused ? "En espera · pausada" : status.label()) + "».");
        }
        switch (action) {
            case PAUSE -> {
                status = WAITING;
                paused = true;
                reason = "Pausada por ti.";
                event("Pausada");
            }
            case RESUME -> {
                status = RUNNING;
                paused = false;
                reason = null;
                event("Reanudada");
            }
            case CANCEL, DISCARD -> {
                status = CANCELLED;
                paused = false;
                reason = action == TaskAction.DISCARD ? "Descartada por ti." : "Cancelada por ti.";
                event(action == TaskAction.DISCARD ? "Descartada" : "Cancelada");
            }
            case RETRY -> {
                attempts++;
                status = QUEUED;
                error = null;
                reason = "Reintento " + attempts + " de " + maxAttempts + ": en cola.";
                resetFailedSteps();
                event("Reintento " + attempts + " de " + maxAttempts);
            }
            case SWITCH_TO_MANUAL -> {
                status = WAITING;
                human = true;
                agent = Agent.raul();
                error = null;
                reason = "Publicación manual: LEEME preparado en la carpeta de la campaña. Raúl publica.";
                event("Pasada a manual con LEEME");
                pendingNotices.add(new SwitchedToManual(id, promotionId));
            }
            case RUN_NOW -> {
                startRunning(now, Math.max(progress, 3));
                event("Ejecución adelantada");
            }
            case START -> {
                status = QUEUED;
                reason = "Aprobada: en cola.";
                event("Aprobada e iniciada");
            }
            case RERUN -> {
                status = QUEUED;
                progress = 0;
                result = null;
                reason = "Relanzada: en cola.";
                steps.replaceAll(s -> new Step(s.label(), 0));
                event("Ejecución relanzada");
            }
            case REOPEN -> {
                status = QUEUED;
                reason = "Reactivada: en cola.";
                event("Reactivada");
            }
            case APPROVE -> {
                checks.replaceAll(c -> new Check(c.label(), true));
                complete("Aprobada. Pasa a programación.");
                event("Aprobada por Raúl");
                pendingNotices.add(new ReviewApproved(id, promotionId));
            }
            case MARK_DONE -> {
                complete("Hecho por Raúl.");
                event("Marcada como hecha");
            }
            case RESCHEDULE -> event("Reprogramada un día después");
            case RECHECK -> event("Comprobado: la dependencia sigue pendiente");
            case REMIND -> event("Aviso preparado para Raúl");
            case CHANGE_AGENT -> {
                Agent next = Agent.ai(newModel == null ? "" : newModel);
                event("Agente cambiado de " + agent.label() + " a " + next.label());
                agent = next;
            }
        }
    }

    /** Las dependencias ya están completadas: la tarea bloqueada pasa a la cola. */
    public boolean unblockIfReady(Set<String> completedTaskIds) {
        if (status != BLOCKED || dependsOn.isEmpty() || !completedTaskIds.containsAll(dependsOn)) {
            return false;
        }
        status = QUEUED;
        reason = "Dependencia resuelta: en cola.";
        event("Dependencia resuelta");
        return true;
    }

    /** El motor (o el simulador del modo demo) empieza a trabajar en una tarea de la cola. */
    public void startFromQueue(Instant now) {
        if (status != QUEUED) throw new DomainException("Solo se puede arrancar una tarea en cola");
        startRunning(now, progress >= 100 ? 0 : progress);
        event("En ejecución");
    }

    /** Avance del trabajo en curso. Al llegar al 100 % la tarea se completa. */
    public void advance(double delta) {
        if (status != RUNNING) throw new DomainException("Solo avanza una tarea en progreso");
        progress = Math.min(100, progress + Math.max(0, delta));
        if (!steps.isEmpty()) {
            double share = 100.0 / steps.size();
            int doneSteps = (int) Math.floor(progress / share);
            for (int i = 0; i < steps.size(); i++) {
                double state = i < doneSteps ? 1 : i == doneSteps ? 0.5 : 0;
                if (steps.get(i).state() < state) steps.set(i, new Step(steps.get(i).label(), state));
            }
        }
        if (progress >= 100) {
            complete(result != null ? result : "Completada.");
            event("Completada");
        }
    }

    private void startRunning(Instant now, double fromProgress) {
        status = RUNNING;
        paused = false;
        reason = null;
        progress = fromProgress;
        startedAt = now;
        if (steps.stream().noneMatch(s -> s.state() > 0 && s.state() < 1)) {
            for (int i = 0; i < steps.size(); i++) {
                if (!steps.get(i).done()) {
                    steps.set(i, new Step(steps.get(i).label(), 0.5));
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
        steps.replaceAll(s -> new Step(s.label(), 1));
        pendingNotices.add(new TaskCompleted(id));
    }

    private void resetFailedSteps() {
        steps.replaceAll(s -> s.failed() ? new Step(s.label(), 0) : s);
    }

    private void event(String message) { pendingEvents.add(message); }

    public List<String> pendingEvents() { return Collections.unmodifiableList(pendingEvents); }
    public List<TaskNotice> pendingNotices() { return Collections.unmodifiableList(pendingNotices); }

    public String id() { return id; }
    public String stationId() { return stationId; }
    public String promotionId() { return promotionId; }
    public String type() { return type; }
    public String title() { return title; }
    public TaskStatus status() { return status; }
    public boolean paused() { return paused; }
    public double progress() { return progress; }
    public Agent agent() { return agent; }
    public String reason() { return reason; }
    public String error() { return error; }
    public String result() { return result; }
    public boolean human() { return human; }
    public int attempts() { return attempts; }
    public int maxAttempts() { return maxAttempts; }
    public Instant startedAt() { return startedAt; }
    public List<Step> steps() { return Collections.unmodifiableList(steps); }
    public List<Check> checks() { return Collections.unmodifiableList(checks); }
    public List<String> dependsOn() { return dependsOn; }
    public int version() { return version; }
}
