package com.socialhub.publishing.adapter.in.scheduling;

import com.socialhub.publishing.PublishingRounds;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Los relojes de publicaciones: cuándo toca cada ronda. */
@Component
@RequiredArgsConstructor
class PublishingSchedules {

    private final PublishingRounds rounds;

    @Scheduled(initialDelayString = "${socialhub.publishing.initial-delay-millis:30000}",
               fixedDelayString = "${socialhub.publishing.tick-millis:60000}")
    void publish() { rounds.publish(); }

    @Scheduled(initialDelayString = "${socialhub.auto.initial-delay-millis:150000}",
               fixedDelayString = "${socialhub.auto.tick-millis:300000}")
    void requestAuto() { rounds.requestAuto(); }

    @Scheduled(initialDelayString = "${socialhub.calendar.initial-delay-millis:90000}",
               fixedDelayString = "${socialhub.calendar.tick-millis:600000}")
    void readCalendar() { rounds.readCalendar(); }

    @Scheduled(initialDelayString = "${socialhub.metrics.initial-delay-millis:180000}",
               fixedDelayString = "${socialhub.metrics.tick-millis:3600000}")
    void readMetrics() { rounds.readMetrics(); }
}
