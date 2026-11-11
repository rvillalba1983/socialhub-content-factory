package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionService;
import com.socialhub.promotion.PromotionSavedHook;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AiProposalsOnSaveTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @TempDir Path ordersFolder;

    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final PromotionService promotionService = mock(PromotionService.class);
    private final AiOrders orders = mock(AiOrders.class);
    private final AiHandlers handlers = mock(AiHandlers.class);
    private final AiAutoOrderRepository done = mock(AiAutoOrderRepository.class);
    private final AiSettings settings = mock(AiSettings.class);
    private final AppModeHolder mode = mock(AppModeHolder.class);
    private final Set<AiAutoOrderEntity.Key> saved = new HashSet<>();
    private final TransactionTemplate transactions = new TransactionTemplate() {
        @Override public void executeWithoutResult(Consumer<TransactionStatus> action) { action.accept(null); }
    };

    @BeforeEach
    void setUp() {
        when(orders.submit(any(), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), any())).thenReturn("t1");
        when(done.existsById(any())).thenAnswer(i -> saved.contains(i.<AiAutoOrderEntity.Key>getArgument(0)));
        doAnswer(i -> {
            AiAutoOrderEntity e = i.getArgument(0);
            saved.add(new AiAutoOrderEntity.Key(e.getPromotionId(), e.getType(), e.getWeekStart()));
            return e;
        }).when(done).save(any());
        when(promotions.brandOrdersFolder("litoques")).thenReturn(Optional.of(ordersFolder.toString()));
        when(settings.dailyLimit()).thenReturn(20);
        when(settings.usedToday()).thenReturn(0L);
    }

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, MADRID);
    }

    private AiProposalsOnSave onSave(ZonedDateTime now) {
        Clock clock = Clock.fixed(now.toInstant(), MADRID);
        var auto = new AiAutoOrders(promotions, promotionService, orders, handlers, done, transactions, clock);
        autoOrders = auto;
        return new AiProposalsOnSave(auto, promotions, settings, mode, clock);
    }

    private AiAutoOrders autoOrders;

    private static PromotionSavedHook.Saved promotion(String status, LocalDate startsOn, LocalDate endsOn) {
        return new PromotionSavedHook.Saved("lit-1", "litoques", status, startsOn, endsOn);
    }

    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void savingAPromotionThatStartsTodayAsksNowAndSaysWhenTheyWillArrive() {
        var p = promotion("PLANNED", LocalDate.of(2026, 11, 9), END);
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(p))
                .contains("Propuestas de la primera semana pedidas: llegarán sobre las 14:40");
        verify(orders, times(1)).submit(eq("lit-1"), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), eq(List.of("2026-11-09")));
    }

    @Test
    void theRoundDoesNotAskAgainWhatSavingAlreadyAsked() {
        var p = promotion("PLANNED", LocalDate.of(2026, 11, 9), END);
        var hook = onSave(at(11, 9, 11, 0));
        hook.afterSave(p);
        when(promotions.proposableDates()).thenReturn(List.of(new PromotionQueries.PromotionDates("lit-1", p.startsOn(), END)));
        when(promotions.proposableBetween(any(), any())).thenReturn(List.of("lit-1"));
        autoOrders.createDue();
        // Y guardar otra vez tampoco la repite ni dice nada nuevo
        assertThat(hook.afterSave(p)).isEmpty();
        verify(orders, times(1)).submit(any(), eq(AiRequestType.WEEKLY_PROPOSALS), any(), any(), any());
    }

    @Test
    void afterTheLastPickupOfTheDayItSaysTomorrow() {
        var p = promotion("ACTIVE", LocalDate.of(2026, 11, 9), END);
        assertThat(onSave(at(11, 9, 21, 0)).afterSave(p))
                .contains("Propuestas de la primera semana pedidas: llegarán mañana sobre las 9:40");
    }

    @Test
    void aPromotionStartingNextMondayWillAskOnThePreviousMondayAtSix() {
        var p = promotion("PLANNED", LocalDate.of(2026, 11, 16), END);
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(p)).contains("Sus propuestas se pedirán el lunes 9 de nov a las 18:00");
        verify(orders, never()).submit(any(), any(), any(), any(), any());
        // Empezando dentro de tres semanas, el lunes anterior es el 16
        var far = promotion("PLANNED", LocalDate.of(2026, 11, 30), END);
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(far)).contains("Sus propuestas se pedirán el lunes 23 de nov a las 18:00");
    }

    @Test
    void theNoteSaysWhatHoldsTheProposalsBack() {
        var p = promotion("PLANNED", LocalDate.of(2026, 11, 9), END);
        when(promotions.brandOrdersFolder("litoques")).thenReturn(Optional.empty());
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(p))
                .contains("Propuestas de la primera semana pedidas, pero no pueden salir: la marca no tiene carpeta de «Encargos»");
    }

    @Test
    void theNoteSaysWhenDriveIsNotAvailableOrTheDailyLimitIsReached() {
        when(promotions.brandOrdersFolder("litoques")).thenReturn(Optional.of(ordersFolder.resolve("no-existe").toString()));
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("ACTIVE", LocalDate.of(2026, 11, 9), END)).orElseThrow())
                .contains("pero no pueden salir: la carpeta de «Encargos» no está disponible en este ordenador");
        saved.clear();
        when(promotions.brandOrdersFolder("litoques")).thenReturn(Optional.of(ordersFolder.toString()));
        when(settings.usedToday()).thenReturn(20L);
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("ACTIVE", LocalDate.of(2026, 11, 9), END)).orElseThrow())
                .contains("se ha alcanzado el límite diario de encargos a Claude (20), saldrán mañana");
    }

    @Test
    void pausedFinishedOrOverPromotionsAndDemoModeSayNothing() {
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("PAUSED", LocalDate.of(2026, 11, 9), END))).isEmpty();
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("FINISHED", LocalDate.of(2026, 11, 9), END))).isEmpty();
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("PLANNED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 8)))).isEmpty();
        // Su primera semana ya pasó: lo normal es el encargo semanal, no hay nada que decir
        assertThat(onSave(at(11, 18, 11, 0)).afterSave(promotion("ACTIVE", LocalDate.of(2026, 11, 9), END))).isEmpty();
        when(mode.isDemo()).thenReturn(true);
        assertThat(onSave(at(11, 9, 11, 0)).afterSave(promotion("PLANNED", LocalDate.of(2026, 11, 9), END))).isEmpty();
        verify(orders, never()).submit(any(), any(), any(), any(), any());
    }
}
