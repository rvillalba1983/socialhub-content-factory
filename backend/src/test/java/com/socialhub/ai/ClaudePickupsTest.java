package com.socialhub.ai;

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
    void thePassesAreEveryFiveHoursFromHalfPastMidnight() {
        assertThat(ClaudePasses.times(false).toString()).isEqualTo("[00:30, 05:30, 10:30, 15:30, 20:30]");
        assertThat(ClaudePasses.times(true).toString()).isEqualTo("[00:50, 05:50, 10:50, 15:50, 20:50]");
    }

    @Test
    void onlyThePassesBetweenEightAndSixAreForUrgentOrdersOnly() {
        assertThat(ClaudePasses.times(false).stream().filter(ClaudePasses::urgentOnly).map(Object::toString))
                .containsExactly("10:30", "15:30");
        assertThat(ClaudePasses.times(true).stream().filter(ClaudePasses::urgentOnly).map(Object::toString))
                .containsExactly("10:50", "15:50");
    }

    @Test
    void anUrgentOrderIsPickedUpByTheNextPassWhateverItIs() {
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 7, 0), true))).isEqualTo("2026-11-09 10:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 10, 31), true))).isEqualTo("2026-11-09 15:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 15, 30), true))).isEqualTo("2026-11-09 20:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 20, 30), true))).isEqualTo("2026-11-10 00:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 0, 5), true))).isEqualTo("2026-11-09 00:30");
    }

    @Test
    void anOrderThatCanWaitSkipsTheDaytimePasses() {
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 6, 0), false))).isEqualTo("2026-11-09 20:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 9, 0), false))).isEqualTo("2026-11-09 20:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 20, 30), false))).isEqualTo("2026-11-10 00:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 0, 40), false))).isEqualTo("2026-11-09 05:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(11, 9, 5, 30), false))).isEqualTo("2026-11-09 20:30");
    }

    @Test
    void daylightSavingChangesDoNotMoveThePasses() {
        // El 25 de octubre de 2026 se atrasa la hora; el 29 de marzo de 2026 se adelantó
        assertThat(ClaudePickups.nextAfter(madrid(10, 24, 21, 0), true).toInstant()).isEqualTo(madrid(10, 25, 0, 30));
        assertThat(format(ClaudePickups.nextAfter(madrid(10, 25, 0, 31), true))).isEqualTo("2026-10-25 05:30");
        assertThat(format(ClaudePickups.nextAfter(madrid(3, 28, 21, 0), true))).isEqualTo("2026-03-29 00:30");
        assertThat(ClaudePickups.nextAfter(madrid(3, 29, 6, 0), true).toInstant()).isEqualTo(madrid(3, 29, 10, 30));
    }

    @Test
    void anOrderIsOverdueThirtyMinutesAfterThePassItMissed() {
        Instant sent = madrid(11, 9, 10, 20);
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 10, 59), true)).isFalse();
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 11, 0), true)).isTrue();
        // Enviado a las 10:26: con 5 minutos de sincronización ya no llega a la de las 10:30
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 10, 26), true))).isEqualTo("2026-11-09 15:30");
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 10, 25), true))).isEqualTo("2026-11-09 15:30");
        assertThat(format(ClaudePickups.pickupFor(madrid(11, 9, 10, 24), true))).isEqualTo("2026-11-09 10:30");
        // Un encargo que puede esperar, enviado por la mañana, no va con retraso a las 11:00: le toca la de las 20:30
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 11, 0), false)).isFalse();
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 20, 59), false)).isFalse();
        assertThat(ClaudePickups.overdue(sent, madrid(11, 9, 21, 0), false)).isTrue();
        // Enviado tras las 20:30: lo recoge la de las 0:30 de la noche
        assertThat(ClaudePickups.overdue(madrid(11, 9, 21, 0), madrid(11, 10, 0, 29), false)).isFalse();
        assertThat(ClaudePickups.overdue(madrid(11, 9, 21, 0), madrid(11, 10, 1, 0), false)).isTrue();
    }

    @Test
    void etaTextSaysTheEffectivePass() {
        assertThat(ClaudePickups.etaText(madrid(11, 9, 11, 0), madrid(11, 9, 11, 0), true)).isEqualTo("sobre las 15:30");
        assertThat(ClaudePickups.etaText(madrid(11, 9, 11, 0), madrid(11, 9, 11, 0), false)).isEqualTo("sobre las 20:30");
        assertThat(ClaudePickups.etaText(madrid(11, 9, 20, 40), madrid(11, 9, 20, 40), false)).isEqualTo("mañana sobre las 0:30");
    }

    @Test
    void anOrderIsUrgentWhenItCannotWaitForTheNextCompletePass() {
        // A las 9:00 la siguiente pasada completa de la nube es la de las 20:30
        Instant at = madrid(11, 9, 9, 0);
        assertThat(ClaudePasses.urgent(false, null, at)).isFalse();
        assertThat(ClaudePasses.urgent(false, madrid(11, 9, 12, 0), at)).isTrue();
        assertThat(ClaudePasses.urgent(false, madrid(11, 9, 20, 39), at)).isTrue();
        assertThat(ClaudePasses.urgent(false, madrid(11, 9, 20, 40), at)).isFalse();
        assertThat(ClaudePasses.urgent(false, madrid(11, 9, 23, 0), at)).isFalse();
        // La del PC, 20 minutos más tarde
        assertThat(ClaudePasses.urgent(true, madrid(11, 9, 20, 59), at)).isTrue();
        assertThat(ClaudePasses.urgent(true, madrid(11, 9, 21, 0), at)).isFalse();
        // De noche cualquier pasada es completa: solo es urgente lo que vence antes de la siguiente
        assertThat(ClaudePasses.urgent(false, madrid(11, 9, 22, 0), madrid(11, 9, 21, 0))).isTrue();
        assertThat(ClaudePasses.urgent(false, madrid(11, 10, 1, 0), madrid(11, 9, 21, 0))).isFalse();
    }

    @Test
    void anOrderCanBeMetOnlyIfSomePassThatDoesItFitsBeforeItsLimit() {
        Instant at = madrid(11, 9, 11, 0);
        // Pasada de las 15:30 (urgente) con 10 minutos de margen
        assertThat(ClaudePasses.canMeet(false, madrid(11, 9, 15, 40), at)).isTrue();
        assertThat(ClaudePasses.canMeet(false, madrid(11, 9, 15, 39), at)).isFalse();
        // Que puede esperar: la de las 20:30
        assertThat(ClaudePasses.canMeet(false, madrid(11, 9, 22, 0), at)).isTrue();
        // Entre dos pasadas lejanas (5 h) no cabe nada
        assertThat(ClaudePasses.canMeet(true, madrid(11, 9, 12, 0), at)).isFalse();
    }
}
