package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiAutoOrdersTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** 8 nov 2026 es domingo. */
    private static ZonedDateTime at(int day, int hour) { return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, MADRID); }

    @Test
    void theSpecIsAskedOnSundayFromNineForTheComingWeek() {
        assertThat(AiAutoOrders.specWeek(at(8, 8))).isEmpty();
        assertThat(AiAutoOrders.specWeek(at(8, 9))).contains(LocalDate.of(2026, 11, 9));
        // Si SocialHub estaba cerrado el domingo, el lunes o el martes se pide el de la semana en curso
        assertThat(AiAutoOrders.specWeek(at(9, 7))).contains(LocalDate.of(2026, 11, 9));
        assertThat(AiAutoOrders.specWeek(at(10, 20))).contains(LocalDate.of(2026, 11, 9));
        assertThat(AiAutoOrders.specWeek(at(11, 12))).isEqualTo(Optional.empty());
    }

    @Test
    void theBalanceIsAskedOnMondayFromNineForTheLastWeek() {
        assertThat(AiAutoOrders.balanceWeek(at(9, 8))).isEmpty();
        assertThat(AiAutoOrders.balanceWeek(at(9, 9))).contains(LocalDate.of(2026, 11, 2));
        assertThat(AiAutoOrders.balanceWeek(at(11, 23))).contains(LocalDate.of(2026, 11, 2));
        assertThat(AiAutoOrders.balanceWeek(at(12, 9))).isEmpty();
        assertThat(AiAutoOrders.balanceWeek(at(8, 10))).isEmpty();
    }

    @Test
    void proposalsAreAskedOnMondayFromSixPmForTheNextWeek() {
        assertThat(AiAutoOrders.proposalsWeek(at(9, 17))).isEmpty();
        assertThat(AiAutoOrders.proposalsWeek(at(9, 18))).contains(LocalDate.of(2026, 11, 16));
        assertThat(AiAutoOrders.proposalsWeek(at(11, 10))).contains(LocalDate.of(2026, 11, 16));
        assertThat(AiAutoOrders.proposalsWeek(at(12, 10))).isEmpty();
    }

    @Test
    void aNewPromotionAsksForItsFirstWeekIfTheUsualMondayHasPassed() {
        // Empieza el lunes 9 y se da de alta el domingo 8: el lunes 2 a las 18:00 ya pasó, así que se pide ya
        assertThat(AiAutoOrders.firstWeek(at(8, 19), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 31)))
                .contains(new AiAutoOrders.FirstWeek(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)));
        // Empieza el miércoles 11 y se da de alta el jueves 12: solo los días que quedan
        assertThat(AiAutoOrders.firstWeek(at(12, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13)))
                .contains(new AiAutoOrders.FirstWeek(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13)));
        // Empieza dentro de tres semanas: se pedirá sola el lunes anterior
        assertThat(AiAutoOrders.firstWeek(at(8, 19), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31))).isEmpty();
        // Su primera semana ya terminó
        assertThat(AiAutoOrders.firstWeek(at(16, 10), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 31))).isEmpty();
    }
}
