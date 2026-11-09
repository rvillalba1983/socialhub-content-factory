package com.socialhub.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ClaudePickupsTest {

    private static Instant madrid(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ClaudePickups.ZONE).toInstant();
    }

    private static String format(ZonedDateTime t) { return t.toLocalDate() + " " + t.toLocalTime(); }

    @Test
    void beforeTheFirstPickupTheNextOneIsNineForty() {
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 7, 0)))).isEqualTo("2026-11-09 09:40");
    }

    @Test
    void betweenPickupsTheNextOneIsTheFollowingOne() {
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 9, 41)))).isEqualTo("2026-11-09 14:40");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 14, 0)))).isEqualTo("2026-11-09 14:40");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 14, 40)))).isEqualTo("2026-11-09 20:40");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 17, 0)))).isEqualTo("2026-11-09 20:40");
    }

    @Test
    void afterTheLastPickupTheNextOneIsNineFortyTomorrow() {
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 20, 40)))).isEqualTo("2026-11-10 09:40");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 23, 59)))).isEqualTo("2026-11-10 09:40");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 0, 5)))).isEqualTo("2026-11-09 09:40");
    }

    @Test
    void daylightSavingChangesDoNotMoveThePickups() {
        // El 25 de octubre de 2026 se atrasa la hora; el 29 de marzo de 2026 se adelantó
        assertThat(format(ClaudePickups.nextAfter(madrid(10, 24, 21, 0)))).isEqualTo("2026-10-25 09:40");
        assertThat(ClaudePickups.nextAfter(madrid(10, 24, 21, 0)).toInstant()).isEqualTo(madrid(10, 25, 9, 40));
        assertThat(format(ClaudePickups.nextAfter(madrid(3, 28, 21, 0)))).isEqualTo("2026-03-29 09:40");
        assertThat(ClaudePickups.nextAfter(madrid(3, 28, 21, 0)).toInstant()).isEqualTo(madrid(3, 29, 9, 40));
    }

    @Test
    void anOrderIsOverdueThirtyMinutesAfterThePickupItMissed() {
        Instant sent = madrid(11, 9, 9, 30);
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 10, 9))).isFalse();
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 10, 10))).isTrue();
        // Enviado a las 9:36: con 5 minutos de sincronización ya no llega a la de las 9:40
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 9, 36)))).isEqualTo("2026-11-09 14:40");
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 9, 35)))).isEqualTo("2026-11-09 14:40");
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 9, 34)))).isEqualTo("2026-11-09 09:40");
        // Enviado justo después de la recogida: espera a la siguiente
        assertThat(ClaudePickups.overdue(madrid(11, 9, 9, 45), madrid(11, 9, 14, 0))).isFalse();
        assertThat(ClaudePickups.overdue(madrid(11, 9, 9, 45), madrid(11, 9, 15, 10))).isTrue();
        // Enviado tras las 20:40: lo recoge la de las 9:40 del día siguiente
        assertThat(ClaudePickups.overdue(madrid(11, 9, 21, 0), madrid(11, 10, 9, 0))).isFalse();
        assertThat(ClaudePickups.overdue(madrid(11, 9, 21, 0), madrid(11, 10, 10, 10))).isTrue();
    }
}
