package com.socialhub.production.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Puerto de persistencia de las tareas. */
public interface TaskRepository {

    Optional<Task> findById(String id);

    List<Task> findByStatus(Collection<TaskStatus> statuses);

    List<Task> findByStation(String stationId);

    Set<String> completedIds();

    /**
     * Guarda los cambios y el historial pendiente. Usa bloqueo optimista: si otro proceso ha cambiado
     * la tarea entretanto, lanza una DomainException.
     */
    void save(Task task);
}
