package com.socialhub.factory.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.ClaudeActivity;
import com.socialhub.armory.ArmoryRegistry;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.InstagramConnection;
import com.socialhub.publishing.PubloraConnection;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La tarjeta «Instagram · API oficial» no se queda en «en espera»: {@link ServicesWatch#refresh()} repite la comprobación
 * una vez cuando hay clave y no hay resultado vigente (también si falla), sin bucles.
 */
class InstagramCardWatchTest {

    private final PromotionQueries promotions = mock(PromotionQueries.class);

    private ServicesWatch watch(InstagramConnection service) {
        return new ServicesWatch(mock(ArmoryRegistry.class), mock(AiSentOrders.class), mock(ClaudeActivity.class),
                mock(PubloraConnection.class), service, promotions, Clock.systemUTC());
    }

    @Test
    void theWatchChecksOnceWhenThereIsAKeyAndNoCheckAndNeverInALoop() {
        InstagramConnection service = mock(InstagramConnection.class);
        when(service.hasKey()).thenReturn(true);
        when(service.hasCheck()).thenReturn(false, true);
        when(service.health()).thenReturn(new InstagramConnection.Health("WAITING", "sin comprobar"));
        when(promotions.brandRefs()).thenReturn(List.of());
        ServicesWatch watch = watch(service);

        watch.refresh();
        watch.refresh();
        watch.refresh();

        verify(service, times(1)).check();
    }

    @Test
    void theWatchDoesNotCheckWithoutAKeyAndSurvivesAFailingCheck() {
        InstagramConnection noKey = mock(InstagramConnection.class);
        when(noKey.hasKey()).thenReturn(false);
        when(noKey.health()).thenReturn(new InstagramConnection.Health("IDLE", "Sin conexión"));
        when(promotions.brandRefs()).thenReturn(List.of());
        watch(noKey).refresh();
        verify(noKey, never()).check();

        InstagramConnection failing = mock(InstagramConnection.class);
        when(failing.hasKey()).thenReturn(true);
        when(failing.hasCheck()).thenReturn(false);
        when(failing.check()).thenThrow(new IllegalStateException("sin red"));
        when(failing.health()).thenReturn(new InstagramConnection.Health("WAITING", "sin comprobar"));
        watch(failing).refresh();
        verify(failing, times(1)).check();
    }
}
