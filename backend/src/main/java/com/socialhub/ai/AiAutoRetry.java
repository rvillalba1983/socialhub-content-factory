package com.socialhub.ai;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Autonomía (fase 8): un encargo a Claude que falla se reintenta solo, sin pulsar «Reintentar», hasta el tercer
 * intento (que sube un nivel de modelo, como el reintento a mano). Espera {@link #WAIT} desde el fallo para no
 * gastar intentos seguidos por un problema pasajero.
 * <p>
 * Los encargos de publicación (Chrome en el PC) no se reintentan aquí: de eso se encarga el motor de publicación,
 * que sigue por otra vía. Lo que pide ayuda a Raúl tampoco: eso no es un fallo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiAutoRetry {

    static final Duration WAIT = Duration.ofMinutes(30);

    private final ProductionService production;
    private final Clock clock;

    @Scheduled(initialDelayString = "${socialhub.ai.retry-initial-delay-millis:120000}",
               fixedDelayString = "${socialhub.ai.retry-millis:300000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                retryDue();
            } catch (RuntimeException e) {
                log.warn("Reintentos automáticos omitidos: {}", e.toString());
            }
        });
    }

    /** @return cuántos encargos se han vuelto a poner en cola */
    int retryDue() {
        Instant now = clock.instant();
        int n = 0;
        for (var task : production.retryableAiTasks()) {
            AiRequestType type = AiRequestType.ofTaskType(task.type()).orElse(null);
            if (type == null || type.automatic()) continue;
            if (now.isBefore(task.failedAt().plus(WAIT))) continue;
            try {
                production.autoRetry(task.id());
                log.info("Encargo {} ({}) reintentado solo", task.id(), type);
                n++;
            } catch (RuntimeException e) {
                log.warn("No se pudo reintentar {}: {}", task.id(), e.getMessage());
            }
        }
        return n;
    }
}
