package com.socialhub.ai;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/** Un envío de un encargo a Claude (tabla {@code ai_request}). */
@Entity
@Table(name = "ai_request")
@Getter
@Setter
@NoArgsConstructor
class AiRequestEntity {

    enum Status { SENT, COMPLETED, FAILED, NEEDS_HUMAN, INVALID }

    @Id private String id;
    private String taskId;
    private String brandId;
    private @Nullable String promotionId;
    private String type;
    @Enumerated(EnumType.STRING) private ClaudeModel model;
    private int attempt;
    @Enumerated(EnumType.STRING) private Status status;
    private String requestPath;
    private @Nullable String resultPath;
    private Instant sentAt;
    private @Nullable Instant finishedAt;
    private @Nullable Long durationMs;
    private @Nullable String runtime;
    private @Nullable String resultModel;
    private @Nullable String error;
}
