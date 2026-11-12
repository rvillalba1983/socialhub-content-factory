package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.ai.AiSentOrders.Sent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PcScheduleTest {

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(PcSchedule.ZONE).toInstant();
    }

    @Test
    void anUrgentOrderIsLateNinetyMinutesAfterThePassItShouldHaveHad() {
        // Enviado a las 10:00: le toca la de las 10:50
        Instant sent = madrid(9, 10, 0);
        assertThat(PcSchedule.passFor(sent, true).toInstant()).isEqualTo(madrid(9, 10, 50));
        assertThat(PcSchedule.late(sent, madrid(9, 12, 19), true)).isFalse();
        assertThat(PcSchedule.late(sent, madrid(9, 12, 20), true)).isTrue();
    }

    @Test
    void anOrderThatCanWaitIsNotLateUntilTheNightPassIsOver() {
        Instant sent = madrid(9, 10, 0);
        assertThat(PcSchedule.passFor(sent, false).toInstant()).isEqualTo(madrid(9, 20, 50));
        assertThat(PcSchedule.late(sent, madrid(9, 15, 0), false)).isFalse();
        assertThat(PcSchedule.late(sent, madrid(9, 22, 19), false)).isFalse();
        assertThat(PcSchedule.late(sent, madrid(9, 22, 20), false)).isTrue();
    }

    @Test
    void anOrderSentAtNightWaitsForTheNextNightPass() {
        // Enviado a las 21:30: la de las 0:50 del día siguiente; la tarea no se apaga, así que la noche cuenta
        Instant sent = madrid(9, 21, 30);
        assertThat(PcSchedule.passFor(sent, false).toInstant()).isEqualTo(madrid(10, 0, 50));
        assertThat(PcSchedule.late(sent, madrid(10, 2, 19), false)).isFalse();
        assertThat(PcSchedule.late(sent, madrid(10, 2, 20), false)).isTrue();
    }

    @Test
    void severalDaysOfSilenceAreLate() {
        assertThat(PcSchedule.late(madrid(6, 12, 0), madrid(9, 11, 0), false)).isTrue();
    }

    @Test
    void silenceCountsOnlyLateOrdersOfThePcWhoseOrdersFolderIsAvailable() {
        Instant now = madrid(9, 12, 30);
        // Con los encargos urgentes (cualquier pasada los recoge): enviado a las 9:00 toca la de las 10:50
        var late = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 0), true, true, true);
        var recent = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 11, 0), true, true, true);
        var waitsForTheNight = new Sent("p", AiRequestType.PUBLISH_CHECK, madrid(9, 9, 0), true, true, false);
        var noDrive = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 8, 45), true, false, true);
        var cloud = new Sent("p", AiRequestType.CAPTIONS, madrid(9, 8, 45), false, true, true);
        var older = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 8, 50), true, true, true);
        var silence = ClaudeActivity.pcSilence(List.of(late, recent, waitsForTheNight, noDrive, cloud, older), now).orElseThrow();
        assertThat(silence.count()).isEqualTo(2);
        assertThat(silence.since()).isEqualTo(madrid(9, 8, 50));
        assertThat(ClaudeActivity.pcSilence(List.of(recent, waitsForTheNight, cloud), now)).isEmpty();
        assertThat(ClaudeActivity.pcSilence(List.of(), now)).isEmpty();
    }

    @Test
    void sinceSaysTodayYesterdayOrTheDate() {
        Instant now = madrid(9, 12, 0);
        assertThat(ClaudeActivity.since(madrid(9, 9, 5), now)).isEqualTo("las 9:05");
        assertThat(ClaudeActivity.since(madrid(8, 22, 10), now)).isEqualTo("ayer a las 22:10");
        assertThat(ClaudeActivity.since(madrid(6, 9, 5), now)).isEqualTo("el 6/11 a las 9:05");
        assertThat(ClaudeActivity.dateTime(madrid(9, 14, 42))).isEqualTo("9/11/2026 14:42");
    }
}
