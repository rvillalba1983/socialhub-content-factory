package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.armory.ArmoryRegistry;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.SecretStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** La comprobación de la tarjeta «Instagram · API oficial» deja siempre su resultado, aunque Meta falle. */
class InstagramCheckResultTest {

    private final InstagramCredentials credentials = mock(InstagramCredentials.class);
    private final InstagramGateway gateway = mock(InstagramGateway.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final InstagramService instagram = new InstagramService(mock(SecretStore.class), credentials, gateway,
            mock(InstagramAccountRepository.class), promotions, mock(ArmoryRegistry.class), mock(ApplicationEventPublisher.class));

    @Test
    void aCheckThatFailsAtMetaStillLeavesItsResult() {
        when(credentials.activeToken()).thenReturn(Optional.of("token-de-prueba"));
        when(credentials.connection()).thenReturn(Optional.empty());
        when(promotions.brandRefs()).thenReturn(List.of());
        when(gateway.me(any())).thenThrow(new InstagramException(InstagramException.Kind.REJECTED, "Meta no responde"));
        assertThat(instagram.hasCheck()).isFalse();
        assertThat(instagram.health().status()).isEqualTo("WAITING");

        instagram.check();

        assertThat(instagram.hasCheck()).isTrue();
        assertThat(instagram.health().status()).isEqualTo("FAILED");
        assertThat(instagram.health().note()).contains("Meta no responde");
    }

    @Test
    void anUnexpectedFailureAlsoLeavesItsResultSoNobodyChecksInALoop() {
        when(credentials.activeToken()).thenReturn(Optional.of("token-de-prueba"));
        when(credentials.connection()).thenReturn(Optional.empty());
        when(promotions.brandRefs()).thenReturn(List.of());
        when(gateway.me(any())).thenThrow(new IllegalStateException("sin red"));

        instagram.check();

        assertThat(instagram.hasCheck()).isTrue();
        assertThat(instagram.health().status()).isEqualTo("FAILED");
    }
}
