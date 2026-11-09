package com.socialhub.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.api.model.RecommendationDto.KindEnum;
import com.socialhub.api.model.RecommendationTargetDto.ScreenEnum;
import com.socialhub.backup.BackupService;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionQueries.BrandRef;
import com.socialhub.promotion.PromotionQueries.PromotionRef;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactoryRecommendationsTest {

    private final AiHeldOrders held = mock(AiHeldOrders.class);
    private final AiSentOrders sent = mock(AiSentOrders.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final PublishingQueries publishing = mock(PublishingQueries.class);
    private final PubloraService publora = mock(PubloraService.class);
    private final BackupService backups = mock(BackupService.class);

    /** Lunes 9 de noviembre de 2026, 11:00 en España. */
    private Instant now = madrid(11, 9, 11, 0);

    private static Instant madrid(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ClaudePickups.ZONE).toInstant();
    }

    private FactoryRecommendations subject() {
        return new FactoryRecommendations(held, sent, promotions, publishing, publora, backups,
                Clock.fixed(now, ClaudePickups.ZONE));
    }

    /** Una marca «Litoques» con una promoción activa, todo en orden. */
    @BeforeEach
    void everythingInOrder() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("litoques", "Litoques")));
        when(promotions.promotionRefs()).thenReturn(List.of(new PromotionRef("lit-1", "litoques", "Test inicial", true)));
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        when(publora.hasAccounts("litoques")).thenReturn(true);
        when(backups.folders()).thenReturn(new BackupService.Folders(Path.of("/copias"), true, Path.of("/local")));
        when(publishing.proposalCounts()).thenReturn(Map.of());
        when(held.current()).thenReturn(List.of());
        when(sent.waiting()).thenReturn(List.of());
    }

    private List<KindEnum> kinds() { return subject().compute().stream().map(RecommendationDto::getKind).toList(); }

    private void backupFolderMissing() {
        when(backups.folders()).thenReturn(new BackupService.Folders(null, false, Path.of("/local")));
    }

    private static AiSentOrders.Sent sentOrder(AiRequestType type, Instant at) {
        return new AiSentOrders.Sent("lit-1", type, at, type.onPc());
    }

    @Test
    void nothingToRecommendGivesAnEmptyList() {
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row1HeldProposalsShowTheRealReasonAndLeadToTheBrandFolders() {
        when(held.current()).thenReturn(List.of(new AiHeldOrders.Held("t1", "est", "Propuestas", "La marca no tiene carpeta de «Encargos».",
                "lit-1", AiRequestType.WEEKLY_PROPOSALS)));
        var all = subject().compute();
        assertThat(all).hasSize(1);
        var r = all.getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.HELD_PROPOSALS);
        assertThat(r.getId()).isEqualTo("held-proposals:lit-1");
        assertThat(r.getTitle()).isEqualTo("Las propuestas de «Test inicial» no pueden salir");
        assertThat(r.getDetail()).isEqualTo("La marca no tiene carpeta de «Encargos».");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.BRAND_FOLDERS);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void otherHeldOrdersAreNotRecommendations() {
        when(held.current()).thenReturn(List.of(
                new AiHeldOrders.Held("t1", "gen", "Textos", "x", "lit-1", AiRequestType.CAPTIONS),
                new AiHeldOrders.Held("t2", "est", "Propuestas", "x", null, AiRequestType.WEEKLY_PROPOSALS)));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row2ProposalsToReviewCountPerPromotion() {
        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 4L));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.PROPOSALS_TO_REVIEW);
        assertThat(r.getTitle()).isEqualTo("Tienes 4 propuestas de «Test inicial» para revisar");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.PROPOSALS);
        assertThat(r.getTarget().getPromotionId()).isEqualTo("lit-1");
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");

        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 1L));
        assertThat(subject().compute().getFirst().getTitle()).isEqualTo("Tienes 1 propuesta de «Test inicial» para revisar");
    }

    @Test
    void row3BrandWithoutAllItsFolders() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.BRAND_FOLDERS_MISSING);
        assertThat(r.getTitle()).isEqualTo("Crea las carpetas de Litoques");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.BRAND_FOLDERS);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void row4OrdersNotCollectedAfterThePickupPlusThirtyMinutes() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 35))));
        now = madrid(11, 9, 10, 9);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 9, 10, 10);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.ORDERS_NOT_COLLECTED);
        assertThat(r.getTitle()).isEqualTo("Claude no ha recogido los encargos de las 9:40. Revisa la tarea programada");
        assertThat(r.getTarget()).isNull();
    }

    @Test
    void row4IgnoresOrdersForTheLaterPickupAndTheOnesDoneOnThePc() {
        // Enviado a las 9:45: toca la de las 14:40, que aún no ha pasado
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 45))));
        now = madrid(11, 9, 11, 0);
        assertThat(subject().compute()).isEmpty();
        // Los del PC los recoge otra tarea
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.PUBLISH_CHECK, madrid(11, 9, 9, 35))));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row4NamesTheMostRecentMissedPickup() {
        when(sent.waiting()).thenReturn(List.of(
                sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 35)),
                sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 14, 0))));
        now = madrid(11, 9, 16, 0);
        assertThat(subject().compute().getFirst().getTitle()).contains("de las 14:40");
    }

    @Test
    void row5BrandWithoutPublora() {
        when(publora.hasAccounts("litoques")).thenReturn(false);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.PUBLORA_MISSING);
        assertThat(r.getTitle()).isEqualTo("Conecta Publora para Litoques");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.PUBLORA);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void row5IsNotShownWhenTheBrandDismissedPublora() {
        when(publora.hasAccounts("litoques")).thenReturn(false);
        when(publora.isDismissed("litoques")).thenReturn(true);
        assertThat(subject().compute()).isEmpty();
        // Al reactivarla vuelve a salir
        when(publora.isDismissed("litoques")).thenReturn(false);
        assertThat(kinds()).containsExactly(KindEnum.PUBLORA_MISSING);
    }

    @Test
    void row6NoBackupFolder() {
        backupFolderMissing();
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.BACKUP_FOLDER_MISSING);
        assertThat(r.getTitle()).isEqualTo("Elige dónde guardar las copias");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.BACKUPS);
    }

    @Test
    void row7BrandWithoutActivePromotion() {
        when(promotions.promotionRefs()).thenReturn(List.of(new PromotionRef("lit-1", "litoques", "Test inicial", false)));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.NO_ACTIVE_PROMOTION);
        assertThat(r.getTitle()).isEqualTo("Da de alta la primera promoción de Litoques");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.NEW_PROMOTION);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void row8ProposalsOnTheWayGiveTheNextPickup() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 10, 50))));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.PROPOSALS_PENDING);
        assertThat(r.getTitle()).isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán sobre las 14:40");
        assertThat(r.getTarget()).isNull();

        now = madrid(11, 9, 22, 0);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 21, 0))));
        assertThat(subject().compute().getFirst().getTitle())
                .isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán mañana sobre las 9:40");
        now = madrid(11, 9, 6, 0);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 8, 21, 0))));
        assertThat(subject().compute().getFirst().getTitle()).endsWith("Llegarán sobre las 9:40");
    }

    @Test
    void otherSentOrdersAreNotProposalsOnTheWay() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 10, 50))));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void priorityFollowsTheOrderOfTheRows() {
        when(held.current()).thenReturn(List.of(new AiHeldOrders.Held("t1", "est", "P", "r", "lit-1", AiRequestType.WEEKLY_PROPOSALS)));
        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 2L));
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 9, 35))));
        now = madrid(11, 9, 10, 30);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        // Con todo a la vez solo salen las 3 primeras
        assertThat(kinds()).containsExactly(KindEnum.HELD_PROPOSALS, KindEnum.PROPOSALS_TO_REVIEW, KindEnum.BRAND_FOLDERS_MISSING);

        // Resueltas las tres primeras, salen las siguientes por su orden
        when(held.current()).thenReturn(List.of());
        when(publishing.proposalCounts()).thenReturn(Map.of());
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(KindEnum.ORDERS_NOT_COLLECTED, KindEnum.PUBLORA_MISSING, KindEnum.BACKUP_FOLDER_MISSING);
    }

    @Test
    void theListHasAtMostThreeAndTheRestShowWhenTheFirstOnesAreSolved() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        when(promotions.promotionRefs()).thenReturn(List.of());
        assertThat(kinds()).containsExactly(KindEnum.BRAND_FOLDERS_MISSING, KindEnum.PUBLORA_MISSING, KindEnum.BACKUP_FOLDER_MISSING);

        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(KindEnum.PUBLORA_MISSING, KindEnum.BACKUP_FOLDER_MISSING, KindEnum.NO_ACTIVE_PROMOTION);

        when(publora.hasAccounts("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(KindEnum.BACKUP_FOLDER_MISSING, KindEnum.NO_ACTIVE_PROMOTION);
    }

    @Test
    void severalBrandsGiveOneRowEach() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("litoques", "Litoques"), new BrandRef("delito", "El DeLito")));
        when(promotions.hasAllBrandFolders("delito")).thenReturn(false);
        var all = subject().compute();
        assertThat(all).extracting(RecommendationDto::getId).contains("brand-folders:delito");
        // El DeLito tampoco tiene Publora ni promoción: la lista se corta en 3 pero cada fila es de su marca
        when(publora.hasAccounts("delito")).thenReturn(true);
        when(promotions.promotionRefs()).thenReturn(List.of(new PromotionRef("lit-1", "litoques", "Test inicial", true)));
        assertThat(subject().compute()).extracting(RecommendationDto::getId)
                .containsExactly("brand-folders:delito", "no-promotion:delito");
    }
}
