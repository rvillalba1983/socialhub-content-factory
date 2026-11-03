package com.socialhub.publishing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Métricas de cada publicación (una lectura, a los 7 días). */
@SecondaryPort
public interface MetricsRepository {

    enum Status { REQUESTED, DONE, FAILED }

    record Metrics(String publicationId, Status status, @Nullable String taskId, int attempts, Instant requestedAt,
                   @Nullable Instant measuredAt, @Nullable Long views, @Nullable Long reach, @Nullable Long likes,
                   @Nullable Long comments, @Nullable Long saves, @Nullable Long shares, @Nullable String note) {}

    Optional<Metrics> find(String publicationId);

    List<Metrics> all();

    void save(Metrics metrics);
}
