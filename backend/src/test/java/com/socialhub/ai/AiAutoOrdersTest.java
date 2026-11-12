package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiAutoOrdersTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** 8 nov 2026 es domingo. */
    private static ZonedDateTime at(int day, int hour) { return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, MADRID); }

    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final PromotionService promotionService = mock(PromotionService.class);
    private final AiOrders orders = mock(AiOrders.class);
    private final AiHandlers handlers = mock(AiHandlers.class);
    private final AiAutoOrderRepository done = mock(AiAutoOrderRepository.class);
    private final AiTypeHandler specHandler = mock(AiTypeHandler.class);
    private final TransactionTemplate transactions = new TransactionTemplate() {
        @Override public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) { action.accept(null); }
    };

    private AiAutoOrders at(ZonedDateTime now) {
        return new AiAutoOrders(promotions, promotionService, orders, handlers, done, transactions, Clock.fixed(now.toInstant(), MADRID));
    }

    @Test
    void theSpecIsOrderedPerBatchOnlyWhenTheHandlerSaysThereIsOneAndNeverWithoutPublications() {
        when(promotions.proposableBetween(any(), any())).thenReturn(List.of("lit-1"));
        when(promotions.proposableDates()).thenReturn(List.of());
        when(handlers.of(AiRequestType.WEEKLY_SPEC)).thenReturn(Optional.of(specHandler));
        when(orders.submit(any(), eq(AiRequestType.WEEKLY_SPEC), any(), any(), any())).thenReturn("t1");
        var now = at(10, 11);
        // Sin lote (nada pendiente, o menos de 3 y lejos): no se pide nada, ni siquiera el domingo
        when(specHandler.autoOrderBatch(eq("lit-1"), any())).thenReturn(List.of());
        assertThat(at(now).createSpecBatches(now)).isZero();
        assertThat(at(at(8, 12)).createSpecBatches(at(8, 12))).isZero();
        verify(orders, never()).submit(any(), eq(AiRequestType.WEEKLY_SPEC), any(), any(), any());
        // Con lote: se pide con las publicaciones del lote como datos de entrada, a cualquier día y hora
        when(specHandler.autoOrderBatch(eq("lit-1"), any())).thenReturn(List.of("p1", "p2", "p3"));
        assertThat(at(now).createSpecBatches(now)).isEqualTo(1);
        verify(orders).submit(eq("lit-1"), eq(AiRequestType.WEEKLY_SPEC), eq(AiRequestType.WEEKLY_SPEC.label() + " · 3 publicaciones"),
                org.mockito.ArgumentMatchers.contains("3 publicaciones"), eq(List.of("p1", "p2", "p3")));
        // El registro semanal ya no interviene
        verify(done, never()).save(any());
    }

    @Test
    void aFailingPromotionDoesNotStopTheOthers() {
        when(promotions.proposableBetween(any(), any())).thenReturn(List.of("a", "b"));
        when(handlers.of(AiRequestType.WEEKLY_SPEC)).thenReturn(Optional.of(specHandler));
        when(specHandler.autoOrderBatch(eq("a"), any())).thenReturn(List.of("x"));
        when(specHandler.autoOrderBatch(eq("b"), any())).thenReturn(List.of("y"));
        when(orders.submit(eq("a"), eq(AiRequestType.WEEKLY_SPEC), any(), any(), any())).thenThrow(new IllegalArgumentException("no"));
        when(orders.submit(eq("b"), eq(AiRequestType.WEEKLY_SPEC), any(), any(), any())).thenReturn("t2");
        var now = at(10, 11);
        assertThat(at(now).createSpecBatches(now)).isEqualTo(1);
        verify(orders).submit(eq("b"), eq(AiRequestType.WEEKLY_SPEC), any(), any(), eq(List.of("y")));
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
    void proposalsForTheNextWeekAreAskedFromMondayAtSixPmUntilSundayNight() {
        var next = LocalDate.of(2026, 11, 16);
        // El lunes 9 antes de las 18:00 no toca (las de esa semana se pidieron la anterior)
        assertThat(AiAutoOrders.proposalsWeek(ZonedDateTime.of(2026, 11, 9, 17, 59, 0, 0, MADRID))).isEmpty();
        assertThat(AiAutoOrders.proposalsWeek(at(9, 18))).contains(next);
        // Martes, miércoles, jueves, sábado y domingo hasta las 23:59: siempre la semana siguiente
        assertThat(AiAutoOrders.proposalsWeek(at(11, 10))).contains(next);
        assertThat(AiAutoOrders.proposalsWeek(at(12, 23))).contains(next);
        assertThat(AiAutoOrders.proposalsWeek(at(14, 9))).contains(next);
        assertThat(AiAutoOrders.proposalsWeek(at(15, 23))).contains(next);
        assertThat(AiAutoOrders.proposalsWeek(ZonedDateTime.of(2026, 11, 15, 23, 59, 0, 0, MADRID))).contains(next);
        // El lunes 16 a las 17:59 no vuelve a pedir la de la semana anterior; a las 18:00 pide la nueva (W+1 = 23)
        assertThat(AiAutoOrders.proposalsWeek(ZonedDateTime.of(2026, 11, 16, 17, 59, 0, 0, MADRID))).isEmpty();
        assertThat(AiAutoOrders.proposalsWeek(at(16, 18))).contains(LocalDate.of(2026, 11, 23));
    }

    @Test
    void theRoundAsksOnThursdayAndSundayNightAndNeverTwiceForTheSameWeek() {
        var next = LocalDate.of(2026, 11, 16);
        when(promotions.proposableBetween(next, next.plusDays(6))).thenReturn(List.of("lit-1"));
        when(orders.submit(any(), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), any())).thenReturn("t1");
        var saved = new java.util.HashSet<AiAutoOrderEntity.Key>();
        when(done.existsById(any())).thenAnswer(i -> saved.contains(i.<AiAutoOrderEntity.Key>getArgument(0)));
        org.mockito.Mockito.doAnswer(i -> {
            AiAutoOrderEntity e = i.getArgument(0);
            saved.add(new AiAutoOrderEntity.Key(e.getPromotionId(), e.getType(), e.getWeekStart()));
            return e;
        }).when(done).save(any());
        // Jueves 12: pide W+1; domingo 15 a las 23:00: no la repite
        assertThat(at(at(12, 10)).createDue()).isEqualTo(1);
        assertThat(at(at(15, 23)).createDue()).isZero();
        verify(orders, org.mockito.Mockito.times(1)).submit(eq("lit-1"), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), eq(List.of("2026-11-16")));
        // Si no estaba pedida, el domingo a las 23:00 la pide
        saved.clear();
        assertThat(at(at(15, 23)).createDue()).isEqualTo(1);
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

    @Test
    void aPlannedPromotionThatStartsTodayIsActivatedAndAsksForItsFirstWeek() {
        // Lunes 9 de nov a las 10:00; la promoción empieza hoy y sigue «Planificada» hasta esta ronda
        var today = LocalDate.of(2026, 11, 9);
        when(promotionService.activateStarted(today)).thenReturn(List.of("lit-1"));
        when(promotions.proposableDates()).thenReturn(List.of(new PromotionQueries.PromotionDates("lit-1", today, LocalDate.of(2026, 12, 31))));
        when(orders.submit(any(), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), any())).thenReturn("t1");
        assertThat(at(at(9, 10)).createDue()).isEqualTo(1);
        verify(promotionService).activateStarted(today);
        verify(orders).submit(eq("lit-1"), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), eq(List.of("2026-11-09")));
        verify(done, org.mockito.Mockito.times(2)).save(any()); // la activación y el encargo
    }

    @Test
    void aPlannedPromotionStartingNextMondayAsksOnThePreviousMondayAtSixAndIsNotActivatedBeforeItsDay() {
        var next = LocalDate.of(2026, 11, 16);
        var end = LocalDate.of(2026, 12, 31);
        when(promotions.proposableDates()).thenReturn(List.of(new PromotionQueries.PromotionDates("lit-2", next, end)));
        when(promotions.proposableBetween(next, next.plusDays(6))).thenReturn(List.of("lit-2"));
        when(orders.submit(any(), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), any())).thenReturn("t2");
        // El registro de lo ya pedido recuerda lo que se guarda, como el real
        var saved = new java.util.HashSet<AiAutoOrderEntity.Key>();
        when(done.existsById(any())).thenAnswer(i -> saved.contains(i.<AiAutoOrderEntity.Key>getArgument(0)));
        org.mockito.Mockito.doAnswer(i -> {
            AiAutoOrderEntity e = i.getArgument(0);
            saved.add(new AiAutoOrderEntity.Key(e.getPromotionId(), e.getType(), e.getWeekStart()));
            return e;
        }).when(done).save(any());
        // Lunes 9 a las 17:00: aún no toca
        assertThat(at(at(9, 17)).createDue()).isZero();
        verify(orders, never()).submit(any(), any(), any(), any(), any());
        // Lunes 9 a las 18:00: se piden las de la semana del 16 (la de siempre y la de la primera semana son la misma)
        assertThat(at(at(9, 18)).createDue()).isGreaterThanOrEqualTo(1);
        verify(orders).submit(eq("lit-2"), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), eq(List.of("2026-11-16")));
        // Hasta el lunes 16 la ronda no la activa (la activación se pide con la fecha de hoy)
        verify(promotionService, org.mockito.Mockito.times(2)).activateStarted(LocalDate.of(2026, 11, 9));
        verify(promotionService, never()).activateStarted(next);
        at(at(16, 9)).createDue();
        verify(promotionService).activateStarted(next);
    }
}
