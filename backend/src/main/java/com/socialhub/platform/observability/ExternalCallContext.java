package com.socialhub.platform.observability;

import io.micrometer.observation.Observation;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/** Datos de una llamada a un sistema externo mientras se observa. */
@Getter
public class ExternalCallContext extends Observation.Context {

    private final String system;
    private final String operation;
    private final @Nullable String detail;
    private final Instant startedAt;
    @Setter private @Nullable String traceId;

    ExternalCallContext(String system, String operation, @Nullable String detail, Instant startedAt) {
        this.system = system;
        this.operation = operation;
        this.detail = detail;
        this.startedAt = startedAt;
    }
}
