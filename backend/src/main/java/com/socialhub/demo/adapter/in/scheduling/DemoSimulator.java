package com.socialhub.demo.adapter.in.scheduling;

import com.socialhub.demo.DemoSimulation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** El reloj del simulador del modo demo (solo existe si el simulador está activo, igual que el simulador). */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${socialhub.demo.enabled:true} and ${socialhub.demo.simulate:true}")
class DemoSimulator {

    private final DemoSimulation simulation;

    @Scheduled(initialDelay = 3_000, fixedDelayString = "${socialhub.demo.tick-millis:1500}")
    void tick() { simulation.tick(); }
}
