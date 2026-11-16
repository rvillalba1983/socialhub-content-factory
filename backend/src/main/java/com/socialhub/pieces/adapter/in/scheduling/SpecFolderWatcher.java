package com.socialhub.pieces.adapter.in.scheduling;

import com.socialhub.pieces.PieceOrders;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 30 s mira las carpetas vigiladas: lo que haya de nuevo se encarga. */
@Component
@RequiredArgsConstructor
class SpecFolderWatcher {

    private final PieceOrders orders;

    @Scheduled(initialDelayString = "${socialhub.pieces.watch-initial-delay-millis:15000}",
               fixedDelayString = "${socialhub.pieces.watch-millis:30000}")
    void watch() { orders.orderNewSpecs(); }
}
