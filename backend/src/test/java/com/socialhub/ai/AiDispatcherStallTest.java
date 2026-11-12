package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.AiTask;
import com.socialhub.production.ProductionService;
import com.socialhub.promotion.PromotionQueries;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** El motivo «Drive no disponible» de las propuestas aguanta las rondas siguientes, cuando la tarea ya está bloqueada. */
class AiDispatcherStallTest {

    @TempDir Path tmp;

    private final ProductionService production = mock(ProductionService.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final AiSettings settings = mock(AiSettings.class);
    private final AiHeldOrders held = new AiHeldOrders();

    private AiDispatcher dispatcher() {
        return new AiDispatcher(production, promotions, settings, mock(AiRequestRepository.class), JsonMapper.builder().build(),
                Clock.systemUTC(), mock(TransactionTemplate.class), mock(AiHandlers.class), List.of(), held,
                new AiUrgentRule(48), mock(ApplicationEventPublisher.class));
    }

    @Test
    void theDriveReasonSurvivesTheRoundsAfterTheTaskIsBlocked() {
        String missing = tmp.resolve("no-existe").toString();
        AiTask task = new AiTask("t1", AiRequestType.WEEKLY_PROPOSALS.taskType(), "lit-1", "Propuestas", "goal",
                List.of("2026-11-09"), 1, "SONNET", Instant.now());
        when(promotions.brandOf("lit-1")).thenReturn("litoques");
        when(promotions.brandOrdersFolder("litoques")).thenReturn(Optional.of(missing));
        var dispatcher = dispatcher();

        // Ronda 1: la tarea está en cola, se bloquea y queda anotado
        when(production.queuedAiTasks()).thenReturn(List.of(task));
        when(production.blockedAiTasks()).thenReturn(List.of());
        AppModeHolder.runIn(com.socialhub.platform.AppMode.REAL, dispatcher::dispatchQueued);
        assertThat(held.stalledProposals()).extracting(AiHeldOrders.Stalled::cause)
                .containsExactly(AiHeldOrders.Cause.DRIVE_UNAVAILABLE);

        // Ronda 2: ya no está en cola sino bloqueada esperando esa carpeta: la fila se mantiene
        when(production.queuedAiTasks()).thenReturn(List.of());
        when(production.blockedAiTasks()).thenReturn(List.of(new ProductionService.BlockedAi(task, missing)));
        AppModeHolder.runIn(com.socialhub.platform.AppMode.REAL, dispatcher::dispatchQueued);
        assertThat(held.stalledProposals()).extracting(AiHeldOrders.Stalled::promotionId).containsExactly("lit-1");

        // Cuando la carpeta aparece, la fila desaparece
        tmp.resolve("no-existe").toFile().mkdirs();
        AppModeHolder.runIn(com.socialhub.platform.AppMode.REAL, dispatcher::dispatchQueued);
        assertThat(held.stalledProposals()).isEmpty();
    }
}
