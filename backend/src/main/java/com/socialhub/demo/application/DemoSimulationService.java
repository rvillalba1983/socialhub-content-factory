package com.socialhub.demo.application;

import com.socialhub.demo.DemoSimulation;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionTasks;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Solo en modo demo: hace avanzar las tareas de ejemplo en la base de datos para que el anillo se
 * mueva y se prueben los avisos en directo.
 */
@Service
@ConditionalOnExpression("${socialhub.demo.enabled:true} and ${socialhub.demo.simulate:true}")
class DemoSimulationService implements DemoSimulation {

    private static final Logger log = LoggerFactory.getLogger(DemoSimulationService.class);

    private final ProductionTasks production;
    private final RandomGenerator random = RandomGenerator.getDefault();

    DemoSimulationService(ProductionTasks production) {
        this.production = production;
        log.info("Simulador del modo demo activo: las tareas de ejemplo avanzan solas en modo demo");
    }

    @Override
    public void tick() {
        // Siempre sobre la base de datos del modo demo, aunque la interfaz muestre el modo real
        try {
            AppModeHolder.runIn(AppMode.DEMO, () -> production.engineTick(random));
        } catch (RuntimeException e) {
            log.debug("Paso del simulador omitido: {}", e.getMessage());
        }
    }
}
