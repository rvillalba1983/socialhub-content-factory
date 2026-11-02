package com.socialhub.demo;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionService;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Solo en modo demo: hace avanzar las tareas de ejemplo en la base de datos para que el anillo se
 * mueva y se prueben los avisos en directo. Desaparece en cuanto haya motor de tareas real (Fase 3).
 */
@Component
@ConditionalOnExpression("${socialhub.demo.enabled:true} and ${socialhub.demo.simulate:true}")
class DemoSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoSimulator.class);

    private final ProductionService production;
    private final AppModeHolder mode;
    private final RandomGenerator random = RandomGenerator.getDefault();

    DemoSimulator(ProductionService production, AppModeHolder mode) {
        this.production = production;
        this.mode = mode;
        log.info("Simulador del modo demo activo: las tareas de ejemplo avanzan solas en modo demo");
    }

    @Scheduled(initialDelay = 3_000, fixedDelayString = "${socialhub.demo.tick-millis:1500}")
    void tick() {
        // Solo en modo demo: en modo real las tareas las mueve el motor de verdad
        if (!mode.isDemo()) return;
        try {
            production.engineTick(random);
        } catch (RuntimeException e) {
            log.debug("Paso del simulador omitido: {}", e.getMessage());
        }
    }
}
