package com.socialhub.ai.application;

import com.socialhub.ai.AiRounds;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class AiRoundsService implements AiRounds {

    private final AiDispatcher dispatcher;
    private final AiResultReader reader;
    private final AiAutoOrders autoOrders;
    private final AiAutoRetry retry;
    private final AiUrgency urgency;

    @Override
    public void dispatchQueued() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                dispatcher.dispatchQueued();
            } catch (RuntimeException e) {
                log.warn("Envío de encargos a Claude omitido: {}", e.toString());
            }
        });
    }

    @Override
    public void readResults() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                reader.readAll();
            } catch (RuntimeException e) {
                log.warn("Lectura de resultados de Claude omitida: {}", e.toString());
            }
        });
    }

    @Override
    public void createWeeklyOrders() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                autoOrders.createDue();
            } catch (RuntimeException e) {
                log.warn("Encargos semanales omitidos: {}", e.toString());
            }
        });
    }

    @Override
    public void retryFailed() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                retry.retryDue();
            } catch (RuntimeException e) {
                log.warn("Reintentos automáticos omitidos: {}", e.toString());
            }
        });
    }

    @Override
    public void raiseUrgency() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                urgency.refresh();
            } catch (RuntimeException e) {
                log.warn("Recálculo de la urgencia de los encargos omitido: {}", e.toString());
            }
        });
    }
}
