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
 * (a y 40, de 8:40 a 22:40) antes del límite de Publer (8 h antes de la hora).
 */
class HandoverCutoffTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void usuallyTheCutoffIsNineHoursBefore() {
        Instant at = at(LocalDate.of(2026, 11, 25), LocalTime.of(17, 0));
        assertThat(PublerRoute.handoverBy(at)).isEqualTo(at.minus(Duration.ofHours(9)));
    }

    @Test
    void whenTheLimitFallsAtNightItIsBeforeTheLastEveningPass() {
        // Publicación a las 8:00: el límite de Publer es medianoche, y la última pasada es a las 22:40
        Instant at = at(LocalDate.of(2026, 11, 25), LocalTime.of(8, 0));
        Instant cutoff = PublerRoute.handoverBy(at);
        assertThat(cutoff.atZone(MADRID).toLocalDateTime()).isEqualTo(LocalDate.of(2026, 11, 24).atTime(22, 10));
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
