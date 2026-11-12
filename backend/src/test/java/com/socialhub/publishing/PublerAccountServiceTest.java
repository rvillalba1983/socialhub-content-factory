package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionQueries.BrandRef;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** «Publer lleno»: qué cuentas lo están y a qué publicaciones retenidas se les puede pasar ya a Publora. */
class PublerAccountServiceTest {

    static final Instant NOW = Instant.now();
    static final String SHARED = "Lito · Litoques · El DeLito";

    private final PublerAccountRepository accounts = mock(PublerAccountRepository.class);
    private final PubloraService publora = mock(PubloraService.class);
    private final PublishingEngine engine = mock(PublishingEngine.class);
    private final PublishJobRepository jobs = mock(PublishJobRepository.class);
    private final PublicationRepository publications = mock(PublicationRepository.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final List<PublishJob> held = new ArrayList<>();

    private PublerAccountService service() {
        return new PublerAccountService(accounts, publora, engine, jobs, publications, promotions, mock(ApplicationEventPublisher.class));
    }

    @BeforeEach
    void twoBrands() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("el-delito", "El DeLito"), new BrandRef("litoques", "Litoques")));
        when(promotions.brandOf("promo-delito")).thenReturn("el-delito");
        when(promotions.brandOf("promo-litoques")).thenReturn("litoques");
        when(jobs.withStatus(any())).thenAnswer(i -> held);
        // Las dos marcas comparten la de TikTok (el nombre, escrito distinto)
        when(accounts.account("el-delito", Platform.TIKTOK)).thenReturn(Optional.of(SHARED));
        when(accounts.account("litoques", Platform.TIKTOK)).thenReturn(Optional.of(" " + SHARED.toLowerCase() + " "));
        when(accounts.account("litoques", Platform.INSTAGRAM)).thenReturn(Optional.of("Litoques"));
        when(accounts.account("el-delito", Platform.INSTAGRAM)).thenReturn(Optional.empty());
        for (var brand : List.of("el-delito", "litoques")) {
            for (var platform : Platform.values()) when(publora.gap(brand, platform)).thenReturn(PubloraService.Gap.NO_ACCOUNT);
        }
    }

    private void heldPublication(String id, String promotion, Platform platform) {
        var p = Publication.builder().id(id).promotionId(promotion).platform(platform).type("Vídeo").title(id)
                .scheduledOn(LocalDate.now().plusDays(20)).scheduledTime(LocalTime.NOON).status(PublicationStatus.SCHEDULED).build();
        when(publications.findById(id)).thenReturn(Optional.of(p));
        held.add(new PublishJob(id, PublishJob.Status.HELD, 1, "Publer", null, List.of(), null, null, NOW, 1, "x", null, null, null,
                List.of(), NOW, NOW, 0));
    }

    @Test
    void theFullAccountAddsUpTheHeldOnesOfAllTheBrandsThatShareIt() {
        heldPublication("a", "promo-delito", Platform.TIKTOK);
        heldPublication("b", "promo-litoques", Platform.TIKTOK);
        heldPublication("c", "promo-litoques", Platform.INSTAGRAM);
        var full = service().full();
        assertThat(full).hasSize(2);
        var tiktok = full.getFirst();
        assertThat(tiktok.accountName()).isEqualTo(SHARED);
        assertThat(tiktok.platform()).isEqualTo("TIKTOK");
        assertThat(tiktok.waiting()).isEqualTo(2);
        // Sin Publora en ninguna: no se puede pasar nada y se dice para quién falta
        assertThat(tiktok.movable()).isZero();
        assertThat(tiktok.withoutPublora()).containsExactly("El DeLito/TikTok", "Litoques/TikTok");
        assertThat(full.get(1).accountName()).isEqualTo("Litoques");
    }

    @Test
    void aHeldOneWithoutAccountIsNotAFullAccount() {
        heldPublication("d", "promo-delito", Platform.INSTAGRAM);
        assertThat(service().full()).isEmpty();
    }

    @Test
    void onlyThoseOfThatAccountWithPubloraMoveAndTheOthersStayHeld() {
        heldPublication("a", "promo-delito", Platform.TIKTOK);
        heldPublication("b", "promo-litoques", Platform.TIKTOK);
        heldPublication("c", "promo-litoques", Platform.INSTAGRAM);
        // Publora solo está configurada para El DeLito/TikTok y para Litoques/Instagram
        when(publora.gap("el-delito", Platform.TIKTOK)).thenReturn(PubloraService.Gap.NONE);
        when(publora.gap("litoques", Platform.INSTAGRAM)).thenReturn(PubloraService.Gap.NONE);
        when(engine.moveHeld(anyString(), any())).thenReturn(true);

        var tiktok = service().full().getFirst();
        assertThat(tiktok.movable()).isEqualTo(1);
        assertThat(tiktok.withoutPublora()).containsExactly("Litoques/TikTok");

        int moved = service().moveToPublora(SHARED.toUpperCase(), Platform.TIKTOK);

        assertThat(moved).isEqualTo(1);
        verify(engine).moveHeld("a", PublishRoute.Id.PUBLORA);
        verify(engine, never()).moveHeld("b", PublishRoute.Id.PUBLORA); // sin Publora
        verify(engine, never()).moveHeld("c", PublishRoute.Id.PUBLORA); // otra cuenta y red
    }

    @Test
    void bothBrandsSharingTheAccountMoveTogetherWhenBothHavePublora() {
        heldPublication("a", "promo-delito", Platform.TIKTOK);
        heldPublication("b", "promo-litoques", Platform.TIKTOK);
        when(publora.gap("el-delito", Platform.TIKTOK)).thenReturn(PubloraService.Gap.NONE);
        when(publora.gap("litoques", Platform.TIKTOK)).thenReturn(PubloraService.Gap.NONE);
        when(engine.moveHeld(anyString(), any())).thenReturn(true);
        assertThat(service().moveToPublora(SHARED, Platform.TIKTOK)).isEqualTo(2);
    }

    @Test
    void withoutPubloraForAnyOfThemNothingMovesAndItSaysSo() {
        heldPublication("a", "promo-delito", Platform.TIKTOK);
        assertThatThrownBy(() -> service().moveToPublora(SHARED, Platform.TIKTOK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Publora");
        verify(engine, never()).moveHeld(anyString(), any());
    }
}
