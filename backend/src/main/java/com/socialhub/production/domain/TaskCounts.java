package com.socialhub.production.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Cuántas tareas hay en cada situación. {@code waiting} suma las que esperan y las que están en cola. */
public record TaskCounts(int total, int running, int waiting, int blocked, int failed, int completed, int idle,
                         int cancelled) {

    public static TaskCounts of(List<Task> tasks) {
        Map<TaskStatus, Integer> n = new EnumMap<>(TaskStatus.class);
        tasks.forEach(t -> n.merge(t.getStatus(), 1, Integer::sum));
        return new TaskCounts(tasks.size(),
                n.getOrDefault(TaskStatus.RUNNING, 0),
                n.getOrDefault(TaskStatus.WAITING, 0) + n.getOrDefault(TaskStatus.QUEUED, 0),
                n.getOrDefault(TaskStatus.BLOCKED, 0),
                n.getOrDefault(TaskStatus.FAILED, 0),
                n.getOrDefault(TaskStatus.COMPLETED, 0),
                n.getOrDefault(TaskStatus.IDLE, 0),
                n.getOrDefault(TaskStatus.CANCELLED, 0));
    }
}
