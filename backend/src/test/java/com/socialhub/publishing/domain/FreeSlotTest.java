package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreeSlotTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Duration SPACING = FreeSlot.SPACING;

    private static Instant at(String local) { return LocalDateTime.parse(local).atZone(MADRID).toInstant(); }

    @Test
    void roundsUpToTheNextQuarterHour() {
        assertThat(FreeSlot.ceilQuarter(at("2026-11-25T10:01:00"), MADRID)).isEqualTo(at("2026-11-25T10:15:00"));
        assertThat(FreeSlot.ceilQuarter(at("2026-11-25T10:15:00"), MADRID)).isEqualTo(at("2026-11-25T10:15:00"));
        assertThat(FreeSlot.ceilQuarter(at("2026-11-25T10:15:01"), MADRID)).isEqualTo(at("2026-11-25T10:30:00"));
        assertThat(FreeSlot.ceilQuarter(at("2026-11-25T10:46:00"), MADRID)).isEqualTo(at("2026-11-25T11:00:00"));
    }

    @Test
    void freeSlotIsTheStartWhenNothingIsNearby() {
        assertThat(FreeSlot.next(at("2026-11-25T10:20:00"), MADRID, List.of(at("2026-11-25T12:00:00")), SPACING))
                .isEqualTo(at("2026-11-25T10:30:00"));
    }

    @Test
    void skipsPastOthersLessThanThirtyMinutesAway() {
        // 10:30 choca con otra a las 10:45 (15 min); la siguiente libre es 11:15 (30 min después)
        assertThat(FreeSlot.next(at("2026-11-25T10:30:00"), MADRID, List.of(at("2026-11-25T10:45:00")), SPACING))
                .isEqualTo(at("2026-11-25T11:15:00"));
        // A exactamente 30 minutos no choca
        assertThat(FreeSlot.next(at("2026-11-25T10:30:00"), MADRID, List.of(at("2026-11-25T10:00:00")), SPACING))
                .isEqualTo(at("2026-11-25T10:30:00"));
        // Una detrás de otra: encadena
        assertThat(FreeSlot.next(at("2026-11-25T10:30:00"), MADRID,
                List.of(at("2026-11-25T10:30:00"), at("2026-11-25T11:00:00"), at("2026-11-25T11:30:00")), SPACING))
                .isEqualTo(at("2026-11-25T12:00:00"));
    }

    @Test
    void crossesMidnightIntoTheNextDay() {
        var slot = FreeSlot.next(at("2026-11-25T23:50:00"), MADRID, List.of(), SPACING).atZone(MADRID);
        assertThat(slot.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-11-26T00:00:00"));
        var busy = FreeSlot.next(at("2026-11-25T23:40:00"), MADRID, List.of(at("2026-11-25T23:50:00")), SPACING).atZone(MADRID);
        assertThat(busy.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-11-26T00:30:00"));
    }

    @Test
    void worksAcrossTheDaylightSavingChange() {
        // 25 oct 2026: a las 03:00 los relojes vuelven a las 02:00
        var slot = FreeSlot.next(at("2026-10-25T01:50:00"), MADRID, List.of(), SPACING);
        assertThat(slot.isBefore(at("2026-10-25T01:50:00"))).isFalse();
        assertThat(Duration.between(at("2026-10-25T01:50:00"), slot)).isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void clashesIsStrictlyLessThanTheSpacing() {
        assertThat(FreeSlot.clashes(at("2026-11-25T10:00:00"), List.of(at("2026-11-25T10:29:00")), SPACING)).isTrue();
        assertThat(FreeSlot.clashes(at("2026-11-25T10:00:00"), List.of(at("2026-11-25T10:30:00")), SPACING)).isFalse();
        assertThat(FreeSlot.clashes(at("2026-11-25T10:00:00"), List.of(at("2026-11-25T09:31:00")), SPACING)).isTrue();
    }

    @Test
    void neverGoesEarlierThanTheStartingMoment() {
        // Con otra publicación justo antes o justo después, la hora nueva solo se retrasa
        var start = at("2026-11-25T10:20:00");
        for (var taken : List.of(at("2026-11-25T09:50:00"), at("2026-11-25T10:00:00"), at("2026-11-25T10:30:00"), at("2026-11-25T10:45:00"))) {
            assertThat(FreeSlot.next(start, MADRID, List.of(taken), SPACING)).isAfterOrEqualTo(start);
        }
    }
}
