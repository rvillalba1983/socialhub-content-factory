package com.socialhub.ai;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Un encargo a Claude tal como lo ven los manejadores de cada tipo. {@code createdAt}: cuándo se creó la tarea.
 * {@code requestPath}: el último archivo de «para-claude» que se envió para este encargo, si ya se envió alguno.
 */
public record AiJob(String taskId, AiRequestType type, String promotionId, String brandId, String title, String goal,
                    List<String> inputs, Instant createdAt, @Nullable String requestPath) {

    public AiJob(String taskId, AiRequestType type, String promotionId, String brandId, String title, String goal,
                 List<String> inputs, Instant createdAt) {
        this(taskId, type, promotionId, brandId, title, goal, inputs, createdAt, null);
    }
}
