package com.socialhub.factory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.ClaudeActivity;
import com.socialhub.armory.ArmoryService;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.InstagramService;
import com.socialhub.publishing.PubloraService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La tarjeta «Instagram · API oficial» no se queda en «en espera»: {@link ServicesWatch#refresh()} repite la comprobación
 * una vez cuando hay clave y no hay resultado vigente (también si falla), sin bucles.
 */
class InstagramCardWatchTest {

    private final PromotionQueries promotions = mock(PromotionQueries.class);

    private ServicesWatch watch(InstagramService service) {
        return new ServicesWatch(mock(ArmoryService.class), mock(AiSentOrders.class), mock(ClaudeActivity.class),
                mock(PubloraService.class), service, promotions, Clock.systemUTC());
    }

    @Test
    void theWatchChecksOnceWhenThereIsAKeyAndNoCheckAndNeverInALoop() {
        InstagramService service = mock(InstagramService.class);
        when(service.hasKey()).thenReturn(true);
        when(service.hasCheck()).thenReturn(false, true);
        when(service.health()).thenReturn(new InstagramService.Health("WAITING", "sin comprobar"));
        when(promotions.brandRefs()).thenReturn(List.of());
        ServicesWatch watch = watch(service);

        watch.refresh();
        watch.refresh();
        watch.refresh();

        verify(service, times(1)).check();
    }

    @Test
    void theWatchDoesNotCheckWithoutAKeyAndSurvivesAFailingCheck() {
        InstagramService noKey = mock(InstagramService.class);
        when(noKey.hasKey()).thenReturn(false);
        when(noKey.health()).thenReturn(new InstagramService.Health("IDLE", "Sin conexión"));
        when(promotions.brandRefs()).thenReturn(List.of());
        watch(noKey).refresh();
        verify(noKey, never()).check();

        InstagramService failing = mock(InstagramService.class);
        when(failing.hasKey()).thenReturn(true);
        when(failing.hasCheck()).thenReturn(false);
        when(failing.check()).thenThrow(new IllegalStateException("sin red"));
        when(failing.health()).thenReturn(new InstagramService.Health("WAITING", "sin comprobar"));
        watch(failing).refresh();
        verify(failing, times(1)).check();
    }
}
