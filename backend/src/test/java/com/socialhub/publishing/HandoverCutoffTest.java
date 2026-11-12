package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.ai.PcSchedule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * El corte previo: para cualquier hora de publicación, el paso a Publer deja al menos una pasada de Claude con Chrome
 * (cada 5 h: 0:50, 5:50, 10:50, 15:50 y 20:50) antes del límite de Publer (8 h antes de la hora).
 */
class HandoverCutoffTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void usuallyTheCutoffIsNineHoursBefore() {
        // 14:00 - 8 h = 6:00: la pasada de las 5:50 está justo antes del límite y basta con ceder una hora antes
        Instant at = at(LocalDate.of(2026, 11, 25), LocalTime.of(14, 0));
        assertThat(PublerRoute.handoverBy(at)).isEqualTo(at.minus(Duration.ofHours(9)));
    }

    @Test
    void whenThePassBeforeTheLimitIsEarlierTheCutoffIsBeforeThatPass() {
        // Publicación a las 8:00: el límite de Publer es medianoche; la última pasada antes de las 23:50 es la de las 20:50
        Instant at = at(LocalDate.of(2026, 11, 25), LocalTime.of(8, 0));
        Instant cutoff = PublerRoute.handoverBy(at);
        assertThat(cutoff.atZone(MADRID).toLocalDateTime()).isEqualTo(LocalDate.of(2026, 11, 24).atTime(20, 20));
        // Publicación a las 15:00: el límite es a las 7:00 y la última pasada anterior es la de las 5:50
        Instant afternoon = at(LocalDate.of(2026, 11, 25), LocalTime.of(15, 0));
        assertThat(PublerRoute.handoverBy(afternoon).atZone(MADRID).toLocalDateTime())
                .isEqualTo(LocalDate.of(2026, 11, 25).atTime(5, 20));
    }

    @Test
    void thereIsAlwaysAChromePassBetweenTheCutoffAndPublersLimit() {
        LocalDate day = LocalDate.of(2026, 11, 25);
        for (int minutes = 0; minutes < 24 * 60; minutes += 5) {
            Instant at = at(day, LocalTime.of(minutes / 60, minutes % 60));
            Instant cutoff = PublerRoute.handoverBy(at);
            Instant limit = PublerRoute.deadline(at);
            Instant pass = PcSchedule.lastPassAtOrBefore(limit);
            assertThat(cutoff).as("corte de " + at.atZone(MADRID)).isBeforeOrEqualTo(at.minus(Duration.ofHours(9)));
            // Hay una pasada después de que el encargo se sincronice y no más tarde del límite
            assertThat(pass).as("pasada para " + at.atZone(MADRID)).isAfter(cutoff.plus(Duration.ofMinutes(5)));
        }
    }

    private static Instant at(LocalDate day, LocalTime time) { return day.atTime(time).atZone(MADRID).toInstant(); }
}
