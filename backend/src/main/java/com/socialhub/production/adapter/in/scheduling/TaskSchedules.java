package com.socialhub.production.adapter.in.scheduling;

import com.socialhub.production.TaskRounds;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Los relojes del motor de tareas (solo existen si el motor está activo). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "socialhub.engine.enabled", havingValue = "true", matchIfMissing = true)
class TaskSchedules {

    private final TaskRounds rounds;

    /** Cada segundo: toma de la cola lo que toca ejecutar. */
    @Scheduled(initialDelayString = "${socialhub.engine.initial-delay-millis:2000}",
               fixedDelayString = "${socialhub.engine.poll-millis:1000}")
    void poll() { rounds.poll(); }

    /** Cada 30 s: las tareas que esperaban una carpeta vuelven a la cola cuando aparece. */
    @Scheduled(initialDelayString = "${socialhub.engine.initial-delay-millis:2000}",
               fixedDelayString = "${socialhub.engine.folder-check-millis:30000}")
    void requeueWhenFoldersAppear() { rounds.requeueWhenFoldersAppear(); }
}
