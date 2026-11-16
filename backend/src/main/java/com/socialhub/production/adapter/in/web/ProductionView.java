package com.socialhub.production.adapter.in.web;

import com.socialhub.api.model.AlertDto;
import com.socialhub.api.model.StationDto;
import com.socialhub.api.model.TaskDto;
import com.socialhub.production.ProductionQueries;
import com.socialhub.production.domain.ProductionOverview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Vista de producción para la API: estaciones con sus contadores, tareas y avisos. */
@Component
@RequiredArgsConstructor
public class ProductionView {

    private final ProductionQueries queries;
    private final ProductionWebMapper mapper;

    public record Snapshot(List<StationDto> stations, List<TaskDto> tasks, List<AlertDto> alerts) {}

    public Snapshot snapshot() {
        ProductionOverview overview = queries.overview();
        return new Snapshot(overview.stations().stream().map(mapper::toDto).toList(),
                overview.tasks().stream().map(mapper::toDto).toList(),
                queries.alerts().stream().map(mapper::toDto).toList());
    }

    public StationDto station(String stationId) { return mapper.toDto(queries.station(stationId)); }
}
