package com.socialhub.ai.adapter.in.scheduling;

import com.socialhub.ai.AiRounds;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Los relojes del orquestador: cuándo toca cada ronda. */
@Component
@RequiredArgsConstructor
class AiSchedules {

    private final AiRounds rounds;

    @Scheduled(initialDelayString = "${socialhub.ai.initial-delay-millis:10000}",
               fixedDelayString = "${socialhub.ai.dispatch-millis:30000}")
    void dispatch() { rounds.dispatchQueued(); }

    @Scheduled(initialDelayString = "${socialhub.ai.initial-delay-millis:10000}",
               fixedDelayString = "${socialhub.ai.read-millis:30000}")
    void read() { rounds.readResults(); }

    @Scheduled(initialDelayString = "${socialhub.ai.auto-initial-delay-millis:60000}",
               fixedDelayString = "${socialhub.ai.auto-millis:600000}")
    void weeklyOrders() { rounds.createWeeklyOrders(); }

    @Scheduled(initialDelayString = "${socialhub.ai.retry-initial-delay-millis:120000}",
               fixedDelayString = "${socialhub.ai.retry-millis:300000}")
    void retry() { rounds.retryFailed(); }

    @Scheduled(initialDelayString = "${socialhub.ai.urgency-initial-delay-millis:90000}",
               fixedDelayString = "${socialhub.ai.urgency-millis:300000}")
    void urgency() { rounds.raiseUrgency(); }
}
