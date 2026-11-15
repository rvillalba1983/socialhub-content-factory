package com.socialhub.pieces.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Un spec.json que ya se ha encargado: qué archivo (y con qué fecha) y la tarea que lo generó. */
public record PieceOrder(String promotionId, String specPath, Instant specModifiedAt, @Nullable String taskId,
                         Instant createdAt) {}
