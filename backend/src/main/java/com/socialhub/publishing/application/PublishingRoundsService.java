package com.socialhub.publishing.application;

import com.socialhub.publishing.PublishingRounds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Cada ronda llama a la pieza que la hace; la hora de cada una la decide el adaptador de entrada. */
@Service
@RequiredArgsConstructor
class PublishingRoundsService implements PublishingRounds {

    private final PublishingEngine engine;
    private final AutoRequests autoRequests;
    private final ExternalCalendarService calendar;
    private final PublicationMetrics metrics;

    @Override
    public void publish() { engine.run(); }

    @Override
    public void requestAuto() { autoRequests.run(); }

    @Override
    public void readCalendar() { calendar.run(); }

    @Override
    public void readMetrics() { metrics.run(); }
}
