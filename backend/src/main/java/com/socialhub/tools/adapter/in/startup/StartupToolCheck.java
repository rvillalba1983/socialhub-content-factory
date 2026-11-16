package com.socialhub.tools.adapter.in.startup;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionTasks;
import com.socialhub.production.TaskRequest;
import com.socialhub.tools.LocalTools;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Al arrancar, pone en cola (modo real) la comprobación de herramientas locales. Siempre es la misma tarea. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "socialhub.engine.enabled", havingValue = "true", matchIfMissing = true)
class StartupToolCheck {

    static final String TASK_ID = "check-local-tools";

    private final ProductionTasks production;

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                production.submitOrRequeue(TASK_ID, new TaskRequest("gen", null, LocalTools.TASK_TYPE,
                        "Comprobar herramientas locales",
                        "Saber qué herramientas locales funcionan en este ordenador y con qué versión.", List.of(), List.of()),
                        "Comprobaciones locales");
            } catch (RuntimeException e) {
                log.warn("No se pudo poner en cola la comprobación de herramientas: {}", e.toString());
            }
        });
    }
}
