package com.socialhub.production.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Puerto de salida: dónde se guardan las tareas. */
@SecondaryPort
public interface TaskRepository {

    Optional<Task> findById(String id);

    List<Task> findAll(TaskFilter filter);

    List<Task> findByStation(String stationId);

    List<Task> findByStatusIn(Collection<TaskStatus> statuses);

    /** Solo los identificadores de las tareas completadas (no carga las tareas). */
    Set<String> completedIds();

    Task save(Task task);
}
