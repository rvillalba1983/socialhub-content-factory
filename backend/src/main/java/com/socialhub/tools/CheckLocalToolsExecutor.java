package com.socialhub.tools;

import com.socialhub.production.TaskExecutor;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tarea {@code CHECK_LOCAL_TOOLS}: comprueba qué herramientas locales funcionan en este ordenador. */
@Component
@RequiredArgsConstructor
class CheckLocalToolsExecutor implements TaskExecutor {

    static final String TYPE = "CHECK_LOCAL_TOOLS";

    private final LocalToolRegistry registry;

    @Override public String type() { return TYPE; }
    @Override public String agentName() { return "Comprobaciones locales"; }
    @Override public Duration timeout() { return Duration.ofMinutes(3); }

    @Override
    public String run(Run run) throws InterruptedException {
        var results = registry.checkAll(run::log);
        long failed = results.stream().filter(r -> !r.check().ok()).count();
        if (failed > 0) {
            throw new IllegalStateException(failed + " de " + results.size() + " herramientas no funcionan. Mira la armería.");
        }
        return results.size() + " herramientas locales funcionan.";
    }
}
