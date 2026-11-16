package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestId;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudeModel;
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

    @Id private String id;
    private String taskId;
    private String brandId;
    private @Nullable String promotionId;
    private String type;
    @Enumerated(EnumType.STRING) private ClaudeModel model;
    private int attempt;
    @Enumerated(EnumType.STRING) private AiRequest.Status status;
    private String requestPath;
    private @Nullable String resultPath;
    private Instant sentAt;
    private @Nullable Instant finishedAt;
    private @Nullable Long durationMs;
    private @Nullable String runtime;
    private @Nullable String resultModel;
    private @Nullable String error;
    /** Se marcó urgente al enviarlo: lo recoge cualquier pasada de Claude, no solo las completas. */
    private boolean urgent;

    static AiRequestEntity of(AiRequest r) {
        AiRequestEntity e = new AiRequestEntity();
        e.id = r.id().value();
        e.taskId = r.taskId();
        e.brandId = r.brandId();
        e.promotionId = r.promotionId();
        e.type = r.type().name();
        e.model = r.model();
        e.attempt = r.attempt();
        e.status = r.status();
        e.requestPath = r.requestPath();
        e.resultPath = r.resultPath();
        e.sentAt = r.sentAt();
        e.finishedAt = r.finishedAt();
        e.durationMs = r.durationMs();
        e.runtime = r.runtime();
        e.resultModel = r.resultModel();
        e.error = r.error();
        e.urgent = r.urgent();
        return e;
    }

    AiRequest toDomain() {
        return new AiRequest(new AiRequestId(id), taskId, brandId, promotionId, AiRequestType.valueOf(type), model, attempt,
                status, requestPath, resultPath, sentAt, finishedAt, durationMs, runtime, resultModel, error, urgent);
    }
}
