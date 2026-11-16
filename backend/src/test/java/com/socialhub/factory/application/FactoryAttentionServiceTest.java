package com.socialhub.factory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.armory.ArmoryQueries;
import com.socialhub.factory.domain.Attention;
import com.socialhub.factory.domain.FactoryAlert;
import com.socialhub.factory.domain.Recommendation;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionAlert;
import com.socialhub.production.ProductionQueries;
import com.socialhub.publishing.PublerAccounts;
import com.socialhub.publishing.PublishingQueries;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lo que necesita atención: avisos de todos los módulos y recomendaciones, solo en modo real las que dependen de Claude. */
class FactoryAttentionServiceTest {

    private final ProductionQueries production = mock(ProductionQueries.class);
    private final PublishingQueries publishing = mock(PublishingQueries.class);
    private final ArmoryQueries armory = mock(ArmoryQueries.class);
    private final AiHeldOrders held = mock(AiHeldOrders.class);
    private final PublerAccounts publer = mock(PublerAccounts.class);
    private final FactoryRecommendations recommendations = mock(FactoryRecommendations.class);
    private final AppModeHolder mode = mock(AppModeHolder.class);

    private final Recommendation recommendation = new Recommendation("x", Recommendation.Kind.PUBLORA_MISSING, "Conecta Publora");

    private Attention attention(Recommendation... shown) {
        when(production.alerts()).thenReturn(List.of());
        when(armory.blockedServices()).thenReturn(List.of());
        when(publishing.publishAlerts()).thenReturn(List.of());
        when(held.current()).thenReturn(List.of(
                new AiHeldOrders.Held("t1", "est", "Propuestas", "Sin carpeta", "lit-1", AiRequestType.WEEKLY_PROPOSALS),
                new AiHeldOrders.Held("t2", "gen", "Textos", "Sin carpeta", "lit-1", AiRequestType.CAPTIONS),
                new AiHeldOrders.Held("t3", "est", "Propuestas sin promoción", "Sin promoción", null, AiRequestType.WEEKLY_PROPOSALS)));
        when(recommendations.compute()).thenReturn(List.of(shown));
        return new FactoryAttentionService(production, publishing, armory, held, publer, recommendations, mode).attention();
    }

    @Test
    void aFullPublerAccountShowsARowWithTheMoveButtonOnlyWhenSomethingCanMove() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        when(publer.full()).thenReturn(List.of(
                new PublerAccounts.Full("Lito", "TIKTOK", "TikTok", 3, 2, List.of("Litoques/TikTok")),
                new PublerAccounts.Full("Otra", "INSTAGRAM", "Instagram", 1, 0, List.of("El DeLito/Instagram"))));
        var alerts = attention().alerts().stream().filter(a -> a.title().contains("de Publer está llena")).toList();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).title()).isEqualTo("La cuenta «Lito» de Publer está llena: 3 publicaciones en espera");
        assertThat(alerts.get(0).action().label()).isEqualTo("Pasar a Publora");
        assertThat(alerts.get(0).action().accountName()).isEqualTo("Lito");
        assertThat(alerts.get(0).detail()).contains("2 pueden pasar ya").contains("Litoques/TikTok");
        assertThat(alerts.get(1).title()).endsWith("1 publicación en espera");
        assertThat(alerts.get(1).action()).isNull();
        assertThat(alerts.get(1).detail()).isEqualTo("Publora no está configurada para El DeLito/Instagram");
    }

    @Test
    void realModeAddsTheRecommendationsAndDropsTheHeldProposalsFromTheAlerts() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        var heldRecommendation = new Recommendation("held-proposals:lit-1", Recommendation.Kind.HELD_PROPOSALS, "Retenidas")
                .promotionId("lit-1");
        Attention attention = attention(heldRecommendation, recommendation);
        assertThat(attention.recommendations()).containsExactly(heldRecommendation, recommendation);
        // Los demás encargos retenidos siguen en «Necesita atención»; las propuestas que ya están en el bloque, no
        assertThat(attention.alerts()).extracting(FactoryAlert::targetId).containsExactly("t2", "t3");
    }

    @Test
    void heldProposalsOfAPromotionNeverShowAsAlertsEvenWithoutTheirRecommendation() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        // Las de una promoción (t1) salen solo en recomendaciones; la que no tiene promoción (t3) sigue como aviso
        assertThat(attention(recommendation).alerts()).extracting(FactoryAlert::targetId).containsExactly("t2", "t3");
    }

    @Test
    void demoModeHasNoRecommendationsAndNoHeldAlerts() {
        when(mode.selected()).thenReturn(AppMode.DEMO);
        Attention attention = attention();
        assertThat(attention.recommendations()).isEmpty();
        assertThat(attention.alerts()).isEmpty();
        verify(recommendations, never()).compute();
    }

    @Test
    void whatBlocksPublishingAlsoShowsAsAlertsInNeedsAttention() {
        when(mode.selected()).thenReturn(AppMode.REAL);
        var silent = new Recommendation("pc-task-silent", Recommendation.Kind.PC_TASK_SILENT, "La tarea «Publicar con Chrome» no contesta")
                .detail("2 encargos sin contestar");
        var noRoute = new Recommendation("no-publishing-route:litoques", Recommendation.Kind.NO_PUBLISHING_ROUTE, "No hay forma de publicar Litoques");
        var alerts = attention(noRoute, silent, recommendation).alerts();
        assertThat(alerts).extracting(FactoryAlert::title).containsSequence("No hay forma de publicar Litoques", "La tarea «Publicar con Chrome» no contesta");
        var pc = alerts.stream().filter(a -> a.title().contains("Chrome")).findFirst().orElseThrow();
        assertThat(pc.kind()).isEqualTo(FactoryAlert.Kind.FAILED);
        assertThat(pc.targetType()).isEqualTo(FactoryAlert.TargetType.ARMORY);
        assertThat(pc.targetId()).isEqualTo("claude-pc");
        assertThat(pc.detail()).isEqualTo("2 encargos sin contestar");
        var route = alerts.stream().filter(a -> a.title().contains("Litoques")).findFirst().orElseThrow();
        assertThat(route.kind()).isEqualTo(FactoryAlert.Kind.BLOCKED);
        assertThat(route.targetId()).isEqualTo("publora");
    }

    @Test
    void productionAlertsAndBlockedServicesComeThrough() {
        when(mode.selected()).thenReturn(AppMode.DEMO);
        var attention = attention();
        when(production.alerts()).thenReturn(List.of(new ProductionAlert(ProductionAlert.Kind.FAILED, ProductionAlert.Target.TASK,
                "task-1", "gen", "Piezas", "Sin espacio")));
        when(armory.blockedServices()).thenReturn(List.of(new ArmoryQueries.BlockedService("google-drive", "Google Drive", "No disponible")));
        var alerts = new FactoryAttentionService(production, publishing, armory, held, publer, recommendations, mode).attention().alerts();
        assertThat(alerts).extracting(FactoryAlert::targetId).containsExactly("task-1", "google-drive");
        assertThat(alerts.get(1).kind()).isEqualTo(FactoryAlert.Kind.BLOCKED);
        assertThat(alerts.get(1).targetType()).isEqualTo(FactoryAlert.TargetType.ARMORY);
        assertThat(attention.alerts()).isEmpty();
    }
}
