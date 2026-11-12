package com.socialhub.production;

import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.domain.Agent;
import com.socialhub.production.domain.AgentKind;
import com.socialhub.production.domain.AiModel;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationCommandType;
import com.socialhub.production.domain.StationRepository;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCommandType;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de producción: órdenes sobre tareas y estaciones, y avance del trabajo. */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class ProductionService {

    private final TaskRepository tasks;
    private final StationRepository stations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Ejecuta una orden pedida desde la interfaz. Lanza DomainException si no está permitida. */
    @Transactional
    public Task execute(String taskId, TaskCommandType command, @Nullable AiModel model) {
        Task task = task(taskId);
        task.execute(command, model, clock.instant());
        Task saved = persist(task);
        unblockReadyTasks();
        return saved;
    }

    /** Cancela un encargo a Claude en cola que ya no tiene nada que hacer. */
    @Transactional
    public void cancelAi(String taskId) {
        execute(taskId, TaskCommandType.CANCEL, null);
    }

    @Transactional
    public Station execute(String stationId, StationCommandType command) {
        Station station = station(stationId);
        boolean pause = command == StationCommandType.PAUSE;
        if (pause) station.pause(); else station.resume();
        Station saved = stations.save(station);
        for (Task task : tasks.findByStation(stationId)) {
            if (pause && task.getStatus() == TaskStatus.RUNNING) {
                task.execute(TaskCommandType.PAUSE, null, clock.instant());
                persist(task);
            } else if (!pause && task.isPaused()) {
                task.execute(TaskCommandType.RESUME, null, clock.instant());
                persist(task);
            }
        }
        events.publishEvent(new FactoryChanged("station", stationId));
        return saved;
    }

    /**
     * Un paso del motor: avanza lo que está en curso y arranca lo que está en cola en estaciones que
     * aceptan trabajo. En la Fase 2 solo lo usa el simulador del modo demo.
     */
    @Transactional
    public int engineTick(RandomGenerator random) {
        Map<String, Station> byId = stations.findAll().stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));
        int changed = 0;
        for (Task task : tasks.findByStatusIn(EnumSet.of(TaskStatus.RUNNING, TaskStatus.QUEUED))) {
            Station station = byId.get(task.getStationId());
            if (task.getStatus() == TaskStatus.RUNNING) {
                task.advance(0.25 + random.nextDouble() * 0.55, clock.instant());
            } else if (station != null && station.acceptsWork()) {
                task.startFromQueue(clock.instant());
            } else {
                continue;
            }
            persist(task);
            changed++;
        }
        return changed + unblockReadyTasks();
    }

    private int unblockReadyTasks() {
        List<Task> blocked = tasks.findByStatusIn(List.of(TaskStatus.BLOCKED));
        if (blocked.isEmpty()) return 0;
        Set<String> completed = tasks.completedIds();
        int count = 0;
        for (Task task : blocked) {
            if (task.unblockIfReady(completed, clock.instant())) {
                persist(task);
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- motor de tareas (modo real)

    /** Tarea tomada de la cola por el motor. */
    public record Claimed(String id, String type, @Nullable String promotionId, List<String> inputs) {}

    /** Crea una tarea. Si depende de otras sin terminar, queda bloqueada hasta que terminen. */
    @Transactional
    public Task submit(TaskRequest request, String agentName) {
        return submit(UUID.randomUUID().toString(), request, agentName);
    }

    /** Como {@link #submit(TaskRequest, String)}, para otros módulos: devuelve solo el identificador. */
    @Transactional
    public String submitTask(TaskRequest request, String agentName) {
        return submit(request, agentName).getId();
    }

    /**
     * Tarea de identificador fijo (por ejemplo, una comprobación periódica): si ya existe y no está en marcha,
     * vuelve a la cola; si no existe, se crea.
     */
    @Transactional
    public Task submitOrRequeue(String id, TaskRequest request, String agentName) {
        return tasks.findById(id).map(task -> {
            if (task.requeue(clock.instant())) persist(task);
            return task;
        }).orElseGet(() -> submit(id, request, agentName));
    }

    private Task submit(String id, TaskRequest request, String agentName) {
        return submit(id, request, new Agent(AgentKind.LOCAL, agentName), 2);
    }

    private Task submit(String id, TaskRequest request, Agent agent, int maxAttempts) {
        stations.findById(request.stationId())
                .orElseThrow(() -> new IllegalArgumentException("No existe la estación " + request.stationId()));
        Set<String> completed = tasks.completedIds();
        for (String dependency : request.dependsOn()) {
            if (tasks.findById(dependency).isEmpty()) throw new IllegalArgumentException("No existe la tarea " + dependency);
        }
        boolean blocked = !completed.containsAll(request.dependsOn());
        Task task = Task.builder()
                .id(id)
                .stationId(request.stationId())
                .promotionId(request.promotionId())
                .type(request.type())
                .title(request.title())
                .goal(request.goal())
                .inputs(request.inputs())
                .status(blocked ? TaskStatus.BLOCKED : TaskStatus.QUEUED)
                .reason(blocked ? "Espera a que terminen sus dependencias." : "En cola.")
                .agent(agent)
                .attempts(1)
                .maxAttempts(maxAttempts)
                .dependsOn(Set.copyOf(request.dependsOn()))
                .demo(false)
                .build();
        task.log("Creada", clock.instant());
        return persist(task);
    }

    /** Crea un encargo a Claude: tiene 3 intentos (el tercero sube un nivel de modelo). */
    @Transactional
    public String submitAi(TaskRequest request, String model) {
        if (!request.type().startsWith(Task.AI_PREFIX)) {
            throw new IllegalArgumentException("Un encargo a Claude debe tener un tipo " + Task.AI_PREFIX + "…");
        }
        return submit(UUID.randomUUID().toString(), request, Agent.ai(AiModel.valueOf(model)), 3).getId();
    }

    /** Encargos a Claude en cola, por orden de llegada. */
    @Transactional(readOnly = true)
    public List<AiTask> queuedAiTasks() {
        return tasks.findByStatusIn(List.of(TaskStatus.QUEUED)).stream()
                .filter(Task::isAiRequest)
                .map(ProductionService::toAiTask)
                .sorted(java.util.Comparator.comparing(AiTask::createdAt).thenComparing(AiTask::id))
                .toList();
    }

    /** Encargo a Claude bloqueado esperando una carpeta, con la ruta que espera. */
    public record BlockedAi(AiTask task, String waitingFor) {}

    /** Encargos a Claude bloqueados porque les falta una carpeta (salen solos cuando aparece). */
    @Transactional(readOnly = true)
    public List<BlockedAi> blockedAiTasks() {
        return tasks.findByStatusIn(List.of(TaskStatus.BLOCKED)).stream()
                .filter(t -> t.isAiRequest() && t.getWaitingFor() != null)
                .map(t -> new BlockedAi(toAiTask(t), t.getWaitingFor()))
                .sorted(java.util.Comparator.comparing((BlockedAi b) -> b.task().createdAt()).thenComparing(b -> b.task().id()))
                .toList();
    }

    /** Cómo ocupa un encargo a Claude sus datos de entrada. */
    public enum Cover {
        /** Sigue vivo: en cola, en curso, en espera, bloqueado o fallido con reintentos por delante. */
        OPEN,
        /** Ya se completó. */
        DONE,
        /** Falló del todo (sin reintentos): no ocupa nada, pero se sabe cuándo falló. */
        FAILED
    }

    /** Un encargo a Claude, cómo cubre sus datos y cuándo cambió de estado por última vez. */
    public record Covering(AiTask task, Cover cover, java.time.Instant at) {}

    /** Encargos a Claude de un tipo y promoción en cola, vivos, completados o fallidos del todo (no los cancelados). */
    @Transactional(readOnly = true)
    public List<Covering> coveringAiTasks(String type, String promotionId) {
        return tasks.findByTypeAndPromotion(type, promotionId, List.of(TaskStatus.QUEUED, TaskStatus.RUNNING,
                        TaskStatus.WAITING, TaskStatus.BLOCKED, TaskStatus.FAILED, TaskStatus.COMPLETED)).stream()
                .filter(Task::isAiRequest)
                .map(t -> new Covering(toAiTask(t), switch (t.getStatus()) {
                    case COMPLETED -> Cover.DONE;
                    case FAILED -> t.canAutoRetry() ? Cover.OPEN : Cover.FAILED;
                    default -> Cover.OPEN;
                }, t.lastChangeAt()))
                .toList();
    }

    /** Encargo a Claude fallido que aún tiene intentos, con cuándo falló. */
    public record RetryableAi(String id, String type, java.time.Instant failedAt) {}

    @Transactional(readOnly = true)
    public List<RetryableAi> retryableAiTasks() {
        return tasks.findByStatusIn(List.of(TaskStatus.FAILED)).stream()
                .filter(Task::canAutoRetry)
                .map(t -> new RetryableAi(t.getId(), t.getType(), t.lastChangeAt()))
                .toList();
    }

    /** Reintento automático de un encargo a Claude (fase 8). */
    @Transactional
    public void autoRetry(String taskId) {
        Task task = task(taskId);
        task.autoRetry(clock.instant());
        persist(task);
    }

    /** El encargo que espera un resultado de Claude, si lo hay con ese identificador. */
    @Transactional(readOnly = true)
    public java.util.Optional<AiTask> waitingAiTask(String taskId) {
        return tasks.findById(taskId).filter(Task::isWaitingForAi).map(ProductionService::toAiTask);
    }

    @Transactional
    public void delegateToAi(String taskId, String model, String reason) {
        Task task = task(taskId);
        task.delegateToAi(AiModel.valueOf(model), reason, clock.instant());
        persist(task);
    }

    @Transactional
    public void keepQueued(String taskId, String reason) {
        tasks.findById(taskId).ifPresent(t -> {
            if (t.keepQueued(reason, clock.instant())) persist(t);
        });
    }

    /** Un encargo en cola no puede salir porque falta su carpeta: se bloquea sin gastar intento. */
    @Transactional
    public void blockQueued(String taskId, String reason, String path) {
        tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.QUEUED).ifPresent(t -> {
            t.blockRun(reason, path, clock.instant());
            persist(t);
        });
    }

    @Transactional
    public void aiCompleted(String taskId, String result) {
        Task task = task(taskId);
        task.aiCompleted(result, clock.instant());
        persist(task);
        unblockReadyTasks();
    }

    @Transactional
    public void aiFailed(String taskId, String error) {
        Task task = task(taskId);
        task.aiFailed(error, clock.instant());
        persist(task);
    }

    @Transactional
    public void aiNeedsHuman(String taskId, String reason) {
        Task task = task(taskId);
        task.aiNeedsHuman(reason, clock.instant());
        persist(task);
    }

    private static AiTask toAiTask(Task t) {
        String model = t.getAgent().isAi()
                ? java.util.Arrays.stream(AiModel.values()).filter(m -> m.label().equals(t.getAgent().name()))
                        .findFirst().orElse(AiModel.SONNET).name()
                : AiModel.SONNET.name();
        Instant created = t.getHistory().isEmpty() ? Instant.EPOCH : t.getHistory().getFirst().at();
        return new AiTask(t.getId(), t.getType(), t.getPromotionId(), t.getTitle(), t.getGoal(), t.getInputs(),
                t.getAttempts(), model, created);
    }

    /** Toma de la cola hasta {@code limit} tareas de esos tipos en estaciones que aceptan trabajo y las pone en marcha. */
    @Transactional
    public List<Claimed> claim(Set<String> types, int limit) {
        List<Claimed> claimed = new ArrayList<>();
        for (Task task : tasks.claimQueued(types, limit)) {
            task.startFromQueue(clock.instant());
            persist(task);
            claimed.add(new Claimed(task.getId(), task.getType(), task.getPromotionId(), task.getInputs()));
        }
        return claimed;
    }

    @Transactional
    public void finishRun(String taskId, String result) {
        tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.RUNNING).ifPresent(t -> {
            t.finishRun(result, clock.instant());
            persist(t);
            unblockReadyTasks();
        });
    }

    @Transactional
    public void failRun(String taskId, String error) {
        tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.RUNNING).ifPresent(t -> {
            t.failRun(error, clock.instant());
            persist(t);
        });
    }

    /** Falta una carpeta: la tarea en curso se bloquea sin gastar intento. */
    @Transactional
    public void blockRun(String taskId, String reason, String path) {
        tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.RUNNING).ifPresent(t -> {
            t.blockRun(reason, path, clock.instant());
            persist(t);
        });
    }

    /**
     * Las tareas bloqueadas por una carpeta que ya está vuelven a la cola.
     *
     * @param available si la carpeta existe ahora
     * @return cuántas vuelven a la cola
     */
    @Transactional
    public int requeueWhenAvailable(java.util.function.Predicate<String> available) {
        int n = 0;
        for (Task task : tasks.findByStatusIn(List.of(TaskStatus.BLOCKED))) {
            if (task.getWaitingFor() != null && available.test(task.getWaitingFor())
                    && task.resourceAvailable(clock.instant())) {
                persist(task);
                n++;
            }
        }
        return n;
    }

    @Transactional
    public void logRun(String taskId, String message) {
        tasks.findById(taskId).ifPresent(t -> {
            t.log(message, clock.instant());
            persist(t);
        });
    }

    @Transactional
    public void reportProgress(String taskId, double percent) {
        tasks.findById(taskId).ifPresent(t -> {
            t.reportProgress(percent);
            persist(t);
        });
    }

    /** Lo que quedó «en progreso» sin ejecutarse de verdad (un cierre de SocialHub) vuelve a la cola. */
    @Transactional
    public int recoverAfterRestart(Set<String> reallyRunning) {
        int n = 0;
        for (Task task : tasks.findByStatusIn(List.of(TaskStatus.RUNNING))) {
            if (!reallyRunning.contains(task.getId()) && task.recoverAfterRestart(clock.instant())) {
                persist(task);
                n++;
            }
        }
        return n;
    }

    private Task persist(Task task) {
        var notices = List.copyOf(task.pendingNotices());
        Task saved = tasks.save(task);
        notices.forEach(events::publishEvent);
        events.publishEvent(new FactoryChanged("task", task.getId()));
        return saved;
    }

    private Task task(String id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("No existe la tarea " + id));
    }

    private Station station(String id) {
        return stations.findById(id).orElseThrow(() -> new NotFoundException("No existe la estación " + id));
    }
}
