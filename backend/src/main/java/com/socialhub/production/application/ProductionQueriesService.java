package com.socialhub.production.application;

import com.socialhub.platform.NotFoundException;
import com.socialhub.production.ProductionAlert;
import com.socialhub.production.ProductionQueries;
import com.socialhub.production.domain.ProductionOverview;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationRepository;
import com.socialhub.production.domain.StationSummary;
import com.socialhub.production.domain.StatusLevel;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCounts;
import com.socialhub.production.domain.TaskFilter;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ProductionQueriesService implements ProductionQueries {

    private final TaskRepository tasks;
    private final StationRepository stations;

    @Override
    public Task task(String id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("No existe la tarea " + id));
    }

    @Override
    public List<Task> tasks(TaskFilter filter) { return tasks.findAll(filter); }

    @Override
    public List<Station> stations() { return stations.findAll(); }

    @Override
    public ProductionOverview overview() {
        List<Station> allStations = stations.findAll();
        List<Task> allTasks = tasks.findAll(TaskFilter.all());
        Map<String, List<Task>> byStation = allTasks.stream().collect(Collectors.groupingBy(Task::getStationId));
        List<StationSummary> summaries = allStations.stream()
                .map(s -> new StationSummary(s, TaskCounts.of(byStation.getOrDefault(s.getId(), List.of()))))
                .toList();
        return new ProductionOverview(summaries, allTasks);
    }

    @Override
    public StationSummary station(String stationId) {
        Station station = stations.findById(stationId)
                .orElseThrow(() -> new NotFoundException("No existe la estación " + stationId));
        return new StationSummary(station, TaskCounts.of(tasks.findAll(new TaskFilter(stationId, null, Set.of(), null))));
    }

    @Override
    public List<ProductionAlert> alerts() {
        List<ProductionAlert> alerts = new ArrayList<>();
        for (Task t : tasks.findAll(TaskFilter.all())) {
            if (t.getStatus() == TaskStatus.FAILED) {
                alerts.add(new ProductionAlert(ProductionAlert.Kind.FAILED, ProductionAlert.Target.TASK, t.getId(),
                        t.getStationId(), t.getTitle(), Objects.requireNonNullElse(t.getError(), "Error sin detalle")));
            } else if (t.getStatus() == TaskStatus.BLOCKED) {
                alerts.add(new ProductionAlert(ProductionAlert.Kind.BLOCKED, ProductionAlert.Target.TASK, t.getId(),
                        t.getStationId(), t.getTitle(), Objects.requireNonNullElse(t.getReason(), "Bloqueada")));
            }
        }
        for (Station s : stations.findAll()) {
            if (s.getStatus() == StatusLevel.FAILED || s.getStatus() == StatusLevel.BLOCKED) {
                alerts.add(new ProductionAlert(
                        s.getStatus() == StatusLevel.FAILED ? ProductionAlert.Kind.FAILED : ProductionAlert.Kind.BLOCKED,
                        ProductionAlert.Target.STATION, s.getId(), s.getId(), s.getName(),
                        Objects.requireNonNullElse(s.getNote(), s.getStatus() == StatusLevel.FAILED ? "Error" : "Bloqueada")));
            }
        }
        return alerts;
    }
}
