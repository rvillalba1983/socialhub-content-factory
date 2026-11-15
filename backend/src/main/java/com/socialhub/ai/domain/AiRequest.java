package com.socialhub.ai.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * Un envío de un encargo a Claude: cuándo y cómo salió, y qué contestó. Cada intento de una tarea es un envío.
 * {@code urgent}: se marcó urgente al enviarlo (lo recoge cualquier pasada de Claude, no solo las completas).
 */
@AggregateRoot
public record AiRequest(@Identity AiRequestId id, String taskId, String brandId, @Nullable String promotionId,
                        AiRequestType type, ClaudeModel model, int attempt, Status status, String requestPath,
                        @Nullable String resultPath, Instant sentAt, @Nullable Instant finishedAt,
                        @Nullable Long durationMs, @Nullable String runtime, @Nullable String resultModel,
                        @Nullable String error, boolean urgent) {

    public enum Status { SENT, COMPLETED, FAILED, NEEDS_HUMAN, INVALID }

    /** Un envío recién salido, aún sin respuesta. */
    public static AiRequest sent(AiRequestId id, String taskId, String brandId, @Nullable String promotionId,
                                 AiRequestType type, ClaudeModel model, int attempt, String requestPath, Instant sentAt,
                                 boolean urgent) {
        return new AiRequest(id, taskId, brandId, promotionId, type, model, attempt, Status.SENT, requestPath, null,
                sentAt, null, null, null, null, null, urgent);
    }

    /** Con la respuesta de Claude: queda en el estado que indica, con el archivo del resultado y lo que tardó. */
    public AiRequest answered(Status newStatus, String resultFile, Instant finished, long duration,
                              @Nullable String agentRuntime, @Nullable String agentModel, @Nullable String problem) {
        return new AiRequest(id, taskId, brandId, promotionId, type, model, attempt, newStatus, requestPath, resultFile,
                sentAt, finished, duration, agentRuntime, agentModel, problem, urgent);
    }

    /** Igual, pero marcado como urgente. */
    public AiRequest asUrgent() {
        return new AiRequest(id, taskId, brandId, promotionId, type, model, attempt, status, requestPath, resultPath,
                sentAt, finishedAt, durationMs, runtime, resultModel, error, true);
    }
}
