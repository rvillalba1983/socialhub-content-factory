package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.TaskStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Tabla {@code task}. Envers guarda en {@code task_aud} los cambios de estado, agente, motivo, error y
 * resultado; no el avance ni los pasos, que cambian a cada momento.
 */
@Entity
@Table(name = "task")
@Audited
@Getter
@Setter
@NoArgsConstructor
class TaskEntity {

    @Id
    private String id;

    @NotAudited private String stationId;
    @NotAudited private @Nullable String promotionId;
    @NotAudited private String type;
    @NotAudited private String title;
    @NotAudited private String goal;
    @NotAudited private @Nullable String why;

    @NotAudited
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> inputs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private boolean paused;

    @NotAudited private double progress;

    @Embedded
    private AgentEmbeddable agent;

    private @Nullable String reason;
    private @Nullable String error;
    private @Nullable String result;
    private boolean human;
    private int attempts;

    @NotAudited private int maxAttempts;
    @NotAudited private @Nullable Instant startedAt;

    @NotAudited
    @ElementCollection
    @CollectionTable(name = "task_step", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "position")
    private List<TaskStepEmbeddable> steps = new ArrayList<>();

    @NotAudited
    @ElementCollection
    @CollectionTable(name = "task_check", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "position")
    private List<TaskCheckEmbeddable> checks = new ArrayList<>();

    @NotAudited
    @ElementCollection
    @CollectionTable(name = "task_dependency", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "depends_on")
    private Set<String> dependsOn = new LinkedHashSet<>();

    @NotAudited
    @OneToMany(mappedBy = "task", cascade = CascadeType.PERSIST)
    @OrderBy("at, id")
    private List<TaskEventEntity> events = new ArrayList<>();

    @NotAudited
    @Column(name = "is_demo")
    private boolean demo;

    @NotAudited
    @UpdateTimestamp
    private Instant updatedAt;

    /** Nula hasta guardarla: así Spring Data sabe que es nueva y la inserta (persist) en lugar de fusionarla. */
    @Version
    private @Nullable Long version;

    TaskEntity(String id) {
        this.id = id;
    }
}
