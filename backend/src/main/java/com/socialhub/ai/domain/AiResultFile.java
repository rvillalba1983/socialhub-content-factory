package com.socialhub.ai.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Un archivo de resultado de Claude ya leído (cada ruta y fecha de modificación se lee una sola vez). */
public record AiResultFile(String path, Instant modifiedAt, @Nullable String requestId, String outcome,
                           @Nullable String note, Instant readAt) {}
