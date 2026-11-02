package com.socialhub.ai;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Llegó un resultado completado de Claude para un encargo. Los tipos concretos (spec semanal, textos…) lo escuchan
 * para hacer lo que toque con {@code outputs}.
 */
public record AiResultApplied(String taskId, AiRequestType type, @Nullable String promotionId, String resultPath,
                              JsonNode outputs) {}
