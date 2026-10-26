package com.socialhub.production;

import com.socialhub.audit.AuditLog;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationRepository;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskAction;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso del módulo de producción: acciones sobre tareas y estaciones, y avance del trabajo. */
@Service
public class ProductionService {

    public static final String ACTOR_RAUL = "Raúl";
    public static final String ACTOR_ENGINE = "Motor";

    private final TaskRepository tasks;
    private final StationRepository stations;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;
    private final Clock clock;

    public ProductionService(TaskRepository tasks, StationRepository stations, ApplicationEventPublisher events,
                             AuditLog audit, Clock clock) {
        this.tasks = tasks;
        this.stations = stations;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /** Aplica una acción pedida desde la interfaz. Lanza DomainException si no está permitida. */
    @Transactional
    public void perform(String taskId, String actionCode, String model) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("No existe la tarea " + taskId));
        TaskAction action = TaskAction.fromCode(actionCode);
        task.apply(action, model, clock.instant());
        persist(task);
        audit.record(ACTOR_RAUL, "task." + action.code(), "task", taskId,
                action == TaskAction.CHANGE_AGENT ? "Claude " + model : null);
        unblockReadyTasks();
    }

    @Transactional
    public void pauseStation(String stationId) {
        Station station = station(stationId);
        station.pause();
        stations.save(station);
        for (Task task : tasks.findByStation(stationId)) {
            if (task.status() == TaskStatus.RUNNING) {
                task.apply(TaskAction.PAUSE, null, clock.instant());
                persist(task);
            }
        }
        audit.record(ACTOR_RAUL, "station.pause", "station", stationId, null);
        events.publishEvent(new FactoryChanged("station", stationId));
    }

    @Transactional
    public void resumeStation(String stationId) {
        Station station = station(stationId);
        station.resume();
        stations.save(station);
        for (Task task : tasks.findByStation(stationId)) {
            if (task.paused()) {
                task.apply(TaskAction.RESUME, null, clock.instant());
                persist(task);
            }
        }
        audit.record(ACTOR_RAUL, "station.resume", "station", stationId, null);
        events.publishEvent(new FactoryChanged("station", stationId));
    }

    /**
     * Un paso del motor: avanza lo que está en curso y arranca lo que está en cola en estaciones que
     * aceptan trabajo. En la Fase 2 solo lo usa el simulador del modo demo.
     */
    @Transactional
    public int engineTick(RandomGenerator random) {
        Map<String, Station> byId = stations.findAll().stream().collect(Collectors.toMap(Station::id, Function.identity()));
        int changed = 0;
        for (Task task : tasks.findByStatus(EnumSet.of(TaskStatus.RUNNING, TaskStatus.QUEUED))) {
            Station station = byId.get(task.stationId());
            if (task.status() == TaskStatus.RUNNING) {
                task.advance(0.25 + random.nextDouble() * 0.55);
            } else if (station != null && station.acceptsWork()) {
                task.startFromQueue(clock.instant());
            } else {
                continue;
            }
            persist(task);
            changed++;
        }
        changed += unblockReadyTasks();
        return changed;
    }

    private int unblockReadyTasks() {
        Set<String> completed = tasks.completedIds();
        int count = 0;
        for (Task blocked : tasks.findByStatus(List.of(TaskStatus.BLOCKED))) {
            if (blocked.unblockIfReady(completed)) {
                persist(blocked);
                audit.record(ACTOR_ENGINE, "task.unblocked", "task", blocked.id(), null);
                count++;
            }
        }
        return count;
    }

    private void persist(Task task) {
        tasks.save(task);
        task.pendingNotices().forEach(events::publishEvent);
        events.publishEvent(new FactoryChanged("task", task.id()));
    }

    private Station station(String id) {
        return stations.findById(id).orElseThrow(() -> new NotFoundException("No existe la estación " + id));
    }
}
