package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublishJob;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/** Tabla {@code publish_job}. */
@Entity
@Table(name = "publish_job")
@Getter
@Setter
@NoArgsConstructor
class PublishJobEntity {

    @Id private String publicationId;
    @Enumerated(EnumType.STRING) private PublishJob.Status status;
    private int routeIndex;
    private @Nullable String route;
    private @Nullable String taskId;
    private String lateTaskIds;
    private @Nullable String externalId;
    private @Nullable String url;
    private @Nullable Instant nextTryAt;
    private int attempts;
    private @Nullable String lastError;
    private @Nullable String manualFile;
    private @Nullable Instant publishStartedAt;
    private String log;
    private Instant createdAt;
    private Instant updatedAt;
    @Version private long version;
}
