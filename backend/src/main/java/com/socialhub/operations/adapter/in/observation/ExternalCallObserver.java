package com.socialhub.operations.adapter.in.observation;

import com.socialhub.operations.RecordExternalOperations;
import com.socialhub.operations.domain.ExternalOperation;
import com.socialhub.platform.observability.ExternalCallContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Recoge cada operación externa observada y la pasa al registro. */
@Slf4j
@Component
@RequiredArgsConstructor
class ExternalCallObserver implements ObservationHandler<ExternalCallContext> {

    private final RecordExternalOperations operations;
    private final Clock clock;

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ExternalCallContext;
    }

    @Override
    public void onStop(ExternalCallContext context) {
        Throwable error = context.getError();
        long durationMs = Duration.between(context.getStartedAt(), clock.instant()).toMillis();
        if (error == null) {
            log.debug("Operación externa {} {} correcta en {} ms", context.getSystem(), context.getOperation(), durationMs);
        } else {
            log.warn("Operación externa {} {} FALLIDA en {} ms: {}", context.getSystem(), context.getOperation(),
                    durationMs, error.toString());
        }
        operations.record(ExternalOperation.unsaved(context.getSystem(), context.getOperation(),
                error == null ? ExternalOperation.Outcome.SUCCESS : ExternalOperation.Outcome.FAILURE,
                context.getStartedAt(), durationMs, context.getTraceId(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(String.valueOf(error.getMessage()), 2000),
                context.getDetail()));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
