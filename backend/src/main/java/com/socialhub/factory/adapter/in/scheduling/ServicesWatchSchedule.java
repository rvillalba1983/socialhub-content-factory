package com.socialhub.factory.adapter.in.scheduling;

import com.socialhub.factory.ServicesCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** El reloj de la comprobación de servicios: cada minuto. */
@Component
@RequiredArgsConstructor
class ServicesWatchSchedule {

    private final ServicesCheck services;

    @Scheduled(initialDelayString = "${socialhub.services-watch.initial-delay-millis:8000}",
               fixedDelayString = "${socialhub.services-watch.millis:60000}")
    void check() { services.check(); }
}
