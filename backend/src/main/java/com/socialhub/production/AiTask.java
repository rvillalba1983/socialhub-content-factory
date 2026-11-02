package com.socialhub.production;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Lo que el orquestador de IA necesita saber de una tarea que es un encargo a Claude.
 *
 * @param model modelo actual de la tarea: {@code HAIKU}, {@code SONNET} u {@code OPUS}
 * @param attempt intento en curso (1, 2 o 3)
 * @param createdAt cuándo se creó, para enviar por orden de llegada
 */
public record AiTask(String id, String type, @Nullable String promotionId, String title, String goal,
                     List<String> inputs, int attempt, String model, Instant createdAt) {}
