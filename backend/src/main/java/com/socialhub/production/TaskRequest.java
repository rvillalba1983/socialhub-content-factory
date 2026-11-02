package com.socialhub.production;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Petición de una tarea nueva para el motor. */
public record TaskRequest(String stationId, @Nullable String promotionId, String type, String title, String goal,
                          List<String> inputs, List<String> dependsOn) {}
