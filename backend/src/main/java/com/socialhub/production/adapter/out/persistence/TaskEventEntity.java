package com.socialhub.production.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/** Línea del historial de una tarea. Solo se añaden; nunca se modifican. */
@Entity
@Table(name = "task_event")
@Getter
@Setter
@NoArgsConstructor
class TaskEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", updatable = false)
    private TaskEntity task;

    private Instant at;
    private String message;

    TaskEventEntity(TaskEntity task, Instant at, String message) {
        this.task = task;
        this.at = at;
        this.message = message;
    }
}
