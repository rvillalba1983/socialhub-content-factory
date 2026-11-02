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
        stations.findById(request.stationId())
                .orElseThrow(() -> new IllegalArgumentException("No existe la estación " + request.stationId()));
        Set<String> completed = tasks.completedIds();
        for (String dependency : request.dependsOn()) {
            if (tasks.findById(dependency).isEmpty()) throw new IllegalArgumentException("No existe la tarea " + dependency);
        }
        boolean blocked = !completed.containsAll(request.dependsOn());
        Task task = Task.builder()
                .id(UUID.randomUUID().toString())
                .stationId(request.stationId())
                .promotionId(request.promotionId())
                .type(request.type())
                .title(request.title())
                .goal(request.goal())
                .inputs(request.inputs())
                .status(blocked ? TaskStatus.BLOCKED : TaskStatus.QUEUED)
                .reason(blocked ? "Espera a que terminen sus dependencias." : "En cola.")
                .agent(new Agent(AgentKind.LOCAL, agentName))
                .attempts(1)
                .maxAttempts(2)
                .dependsOn(Set.copyOf(request.dependsOn()))
                .demo(false)
                .build();
        task.log("Creada", clock.instant());
        return persist(task);
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

    /** Al arrancar: lo que quedó «en progreso» vuelve a la cola. */
    @Transactional
    public int recoverAfterRestart() {
        int n = 0;
        for (Task task : tasks.findByStatusIn(List.of(TaskStatus.RUNNING))) {
            if (task.recoverAfterRestart(clock.instant())) {
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
