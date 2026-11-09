package com.socialhub.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.api.model.FactoryDto;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.armory.ArmoryQueries;
import com.socialhub.armory.adapter.in.web.ArmoryWebMapper;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.adapter.in.web.ProductionView;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.adapter.in.web.PromotionWebMapper;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.adapter.in.web.PublicationWebMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactoryControllerTest {

    private final ProductionView production = mock(ProductionView.class);
    private final PublishingQueries publishing = mock(PublishingQueries.class);
    private final AppModeHolder mode = mock(AppModeHolder.class);
    private final AiHeldOrders held = mock(AiHeldOrders.class);
    private final FactoryRecommendations recommendations = mock(FactoryRecommendations.class);
    private final PromotionWebMapper promotionMapper = mock(PromotionWebMapper.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final ArmoryQueries armory = mock(ArmoryQueries.class);
    private final ArmoryWebMapper armoryMapper = mock(ArmoryWebMapper.class);
    private final PublicationWebMapper publicationMapper = mock(PublicationWebMapper.class);

    private final RecommendationDto recommendation = new RecommendationDto("x", RecommendationDto.KindEnum.PUBLORA_MISSING, "Conecta Publora");

    private FactoryController controller(RecommendationDto... shown) {
        when(production.snapshot()).thenReturn(new ProductionView.Snapshot(List.of(), List.of(), List.of()));
        when(armoryMapper.toDtos(any())).thenReturn(List.of());
        when(publishing.publishAlerts()).thenReturn(List.of());
        when(held.current()).thenReturn(List.of(
                new AiHeldOrders.Held("t1", "est", "Propuestas", "Sin carpeta", "lit-1", AiRequestType.WEEKLY_PROPOSALS),
                new AiHeldOrders.Held("t2", "gen", "Textos", "Sin carpeta", "lit-1", AiRequestType.CAPTIONS),
                new AiHeldOrders.Held("t3", "est", "Propuestas sin promoción", "Sin promoción", null, AiRequestType.WEEKLY_PROPOSALS)));
        when(recommendations.compute()).thenReturn(List.of(shown));
        return new FactoryController(promotions, promotionMapper, production, publishing, publicationMapper, armory,
                armoryMapper, mode, Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC), held, recommendations);
    }

    @Test
    void realModeAddsTheRecommendationsAndDropsTheHeldProposalsFromTheAlerts() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        var heldRecommendation = new RecommendationDto("held-proposals:lit-1", RecommendationDto.KindEnum.HELD_PROPOSALS, "Retenidas")
                .promotionId("lit-1");
        FactoryDto factory = controller(heldRecommendation, recommendation).getFactory().getBody();
        assertThat(factory.getRecommendations()).containsExactly(heldRecommendation, recommendation);
        // Los demás encargos retenidos siguen en «Necesita atención»; las propuestas que ya están en el bloque, no
        assertThat(factory.getAlerts()).extracting(a -> a.getTargetId()).containsExactly("t2", "t3");
    }

    @Test
    void heldProposalsOfAPromotionNeverShowAsAlertsEvenWithoutTheirRecommendation() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        FactoryDto factory = controller(recommendation).getFactory().getBody();
        // Las de una promoción (t1) salen solo en recomendaciones; la que no tiene promoción (t3) sigue como aviso
        assertThat(factory.getAlerts()).extracting(a -> a.getTargetId()).containsExactly("t2", "t3");
    }

    @Test
    void demoModeHasNoRecommendationsAndNoHeldAlerts() {
        when(mode.selected()).thenReturn(AppMode.DEMO);
        FactoryDto factory = controller().getFactory().getBody();
        assertThat(factory.getRecommendations()).isEmpty();
        assertThat(factory.getAlerts()).isEmpty();
        verify(recommendations, never()).compute();
    }
}
