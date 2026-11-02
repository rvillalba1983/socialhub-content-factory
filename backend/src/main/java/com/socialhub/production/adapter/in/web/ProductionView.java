package com.socialhub.production.adapter.in.web;

import com.socialhub.api.model.AlertDto;
import com.socialhub.api.model.StationDto;
import com.socialhub.api.model.TaskCountsDto;
import com.socialhub.api.model.TaskDto;
import com.socialhub.production.ProductionQueries;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StatusLevel;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskFilter;
import com.socialhub.production.domain.TaskStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Vista de producción para la API: estaciones con sus contadores, tareas y avisos, leídas a la vez. */
@Component
@RequiredArgsConstructor
public class ProductionView {

    private final ProductionQueries queries;
    private final ProductionWebMapper mapper;

    public record Snapshot(List<StationDto> stations, List<TaskDto> tasks, List<AlertDto> alerts) {}

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        List<Station> stations = queries.stations();
        List<Task> tasks = queries.tasks(TaskFilter.all());
        Map<String, List<Task>> byStation = tasks.stream().collect(Collectors.groupingBy(Task::getStationId));
        List<StationDto> stationDtos = stations.stream()
                .map(s -> mapper.toDto(s, counts(byStation.getOrDefault(s.getId(), List.of()))))
                .toList();
        return new Snapshot(stationDtos, tasks.stream().map(mapper::toDto).toList(), alerts(stations, tasks));
    }

    @Transactional(readOnly = true)
    public StationDto station(Station station) {
        List<Task> tasks = queries.tasks(new TaskFilter(station.getId(), null, java.util.Set.of(), null));
        return mapper.toDto(station, counts(tasks));
    }

    static TaskCountsDto counts(List<Task> tasks) {
        Map<TaskStatus, Integer> n = new EnumMap<>(TaskStatus.class);
        tasks.forEach(t -> n.merge(t.getStatus(), 1, Integer::sum));
        return new TaskCountsDto(tasks.size(),
                n.getOrDefault(TaskStatus.RUNNING, 0),
                n.getOrDefault(TaskStatus.WAITING, 0) + n.getOrDefault(TaskStatus.QUEUED, 0),
                n.getOrDefault(TaskStatus.BLOCKED, 0),
                n.getOrDefault(TaskStatus.FAILED, 0),
                n.getOrDefault(TaskStatus.COMPLETED, 0),
                n.getOrDefault(TaskStatus.IDLE, 0),
                n.getOrDefault(TaskStatus.CANCELLED, 0));
    }

    /** Lo que necesita atención de Raúl: tareas con error o bloqueadas y estaciones bloqueadas o caídas. */
    static List<AlertDto> alerts(List<Station> stations, List<Task> tasks) {
        List<AlertDto> alerts = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getStatus() == TaskStatus.FAILED) {
                alerts.add(new AlertDto(AlertDto.KindEnum.FAILED, AlertDto.TargetTypeEnum.TASK, t.getId(),
                        t.getStationId(), t.getTitle(), Objects.requireNonNullElse(t.getError(), "Error sin detalle")));
            } else if (t.getStatus() == TaskStatus.BLOCKED) {
                alerts.add(new AlertDto(AlertDto.KindEnum.BLOCKED, AlertDto.TargetTypeEnum.TASK, t.getId(),
                        t.getStationId(), t.getTitle(), Objects.requireNonNullElse(t.getReason(), "Bloqueada")));
            }
        }
        for (Station s : stations) {
            if (s.getStatus() == StatusLevel.FAILED || s.getStatus() == StatusLevel.BLOCKED) {
                alerts.add(new AlertDto(
                        s.getStatus() == StatusLevel.FAILED ? AlertDto.KindEnum.FAILED : AlertDto.KindEnum.BLOCKED,
                        AlertDto.TargetTypeEnum.STATION, s.getId(), s.getId(), s.getName(),
                        Objects.requireNonNullElse(s.getNote(), s.getStatus() == StatusLevel.FAILED ? "Error" : "Bloqueada")));
            }
        }
        return alerts;
    }
}
