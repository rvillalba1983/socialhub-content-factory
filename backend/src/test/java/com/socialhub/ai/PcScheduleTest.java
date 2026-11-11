package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.ai.AiSentOrders.Sent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PcScheduleTest {

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(PcSchedule.ZONE).toInstant();
    }

    @Test
    void duringTheScheduleItCountsAllTheTime() {
        assertThat(PcSchedule.activeTime(madrid(9, 10, 0), madrid(9, 11, 30))).isEqualTo(Duration.ofMinutes(90));
        assertThat(PcSchedule.late(madrid(9, 10, 0), madrid(9, 11, 29))).isFalse();
        assertThat(PcSchedule.late(madrid(9, 10, 0), madrid(9, 11, 30))).isTrue();
    }

    @Test
    void anOrderSentAtNightIsNotLateUntilTenTenTheNextDay() {
        Instant sent = madrid(9, 23, 0);
        assertThat(PcSchedule.activeTime(sent, madrid(10, 8, 40))).isEqualTo(Duration.ZERO);
        assertThat(PcSchedule.late(sent, madrid(10, 10, 9))).isFalse();
        assertThat(PcSchedule.late(sent, madrid(10, 10, 10))).isTrue();
    }

    @Test
    void anOrderSentLateInTheDayAddsTheTwoPartsAcrossTheNight() {
        // 22:00 a 22:40 son 40 minutos; el resto, desde las 8:40 del día siguiente: faltan 50
        Instant sent = madrid(9, 22, 0);
        assertThat(PcSchedule.late(sent, madrid(10, 9, 29))).isFalse();
        assertThat(PcSchedule.late(sent, madrid(10, 9, 30))).isTrue();
        // Antes de abrir por la mañana
        assertThat(PcSchedule.late(madrid(9, 5, 0), madrid(9, 10, 9))).isFalse();
        assertThat(PcSchedule.late(madrid(9, 5, 0), madrid(9, 10, 10))).isTrue();
    }

    @Test
    void severalDaysOfSilenceAreLate() {
        assertThat(PcSchedule.late(madrid(6, 12, 0), madrid(9, 11, 0))).isTrue();
        assertThat(PcSchedule.activeTime(madrid(6, 12, 0), madrid(8, 12, 0)))
                .isEqualTo(Duration.ofHours(10).plusMinutes(40).plus(Duration.ofHours(14)).plus(Duration.ofHours(3)).plusMinutes(20));
    }

    @Test
    void silenceCountsOnlyLateOrdersOfThePcWhoseOrdersFolderIsAvailable() {
        Instant now = madrid(9, 12, 0);
        var late = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 0), true, true);
        var recent = new Sent("p", AiRequestType.PUBLISH_CHECK, madrid(9, 11, 0), true, true);
        var noDrive = new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 8, 45), true, false);
        var cloud = new Sent("p", AiRequestType.CAPTIONS, madrid(9, 8, 45), false, true);
        var older = new Sent("p", AiRequestType.METRICS_READ, madrid(9, 8, 50), true, true);
        var silence = ClaudeActivity.pcSilence(List.of(late, recent, noDrive, cloud, older), now).orElseThrow();
        assertThat(silence.count()).isEqualTo(2);
        assertThat(silence.since()).isEqualTo(madrid(9, 8, 50));
        assertThat(ClaudeActivity.pcSilence(List.of(recent, cloud), now)).isEmpty();
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
