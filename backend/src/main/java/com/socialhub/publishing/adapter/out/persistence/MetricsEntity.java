package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.MetricsRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Tabla {@code publication_metric}. */
@Entity
@Table(name = "publication_metric")
@Getter
@NoArgsConstructor
@AllArgsConstructor
class MetricsEntity {
    @Id private String publicationId;
    @Enumerated(EnumType.STRING) private MetricsRepository.Status status;
    private @Nullable String taskId;
    private int attempts;
    private Instant requestedAt;
    private @Nullable Instant measuredAt;
    private @Nullable Long views;
    private @Nullable Long reach;
    private @Nullable Long likes;
    private @Nullable Long comments;
    private @Nullable Long saves;
    private @Nullable Long shares;
    private @Nullable String note;
}
