package com.socialhub.production;

import com.socialhub.platform.NotFoundException;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationRepository;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskFilter;
import com.socialhub.production.domain.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de producción (solo lectura). */
@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionQueries {

    private final TaskRepository tasks;
    private final StationRepository stations;

    public Task task(String id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("No existe la tarea " + id));
    }

    public List<Task> tasks(TaskFilter filter) { return tasks.findAll(filter); }

    public List<Station> stations() { return stations.findAll(); }
}
