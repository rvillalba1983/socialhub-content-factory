package com.socialhub.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.ClaudePickups;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.api.model.RecommendationDto.KindEnum;
import com.socialhub.api.model.RecommendationTargetDto.ScreenEnum;
import com.socialhub.backup.BackupService;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionQueries.BrandRef;
import com.socialhub.promotion.PromotionQueries.PromotionRef;
import com.socialhub.publishing.PublerAccountService;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private final PublerAccountService publer = mock(PublerAccountService.class);
    private final com.socialhub.ai.AiAutoStarted autoStarted = mock(com.socialhub.ai.AiAutoStarted.class);
    private final BackupService backups = mock(BackupService.class);

    /** Lunes 9 de noviembre de 2026, 11:00 en España. */
    private Instant now = madrid(11, 9, 11, 0);

    private static Instant madrid(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ClaudePickups.ZONE).toInstant();
    }

    private static PromotionRef ref(String status, LocalDate startsOn, LocalDate endsOn) {
        return new PromotionRef("lit-1", "litoques", "Test inicial", status, startsOn, endsOn);
    }

    private FactoryRecommendations subject() {
        return new FactoryRecommendations(held, sent, promotions, publishing, publora, publer, backups, autoStarted,
                Clock.fixed(now, ClaudePickups.ZONE));
    }

    /** Una marca «Litoques» con una promoción activa, todo en orden. */
    @BeforeEach
    void everythingInOrder() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("litoques", "Litoques")));
        when(promotions.promotionRefs()).thenReturn(List.of(ref("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        when(publora.hasAccounts("litoques")).thenReturn(true);
        when(backups.hasChosenFolder()).thenReturn(true);
        when(publishing.proposalCounts()).thenReturn(Map.of());
        when(held.current()).thenReturn(List.of());
        when(sent.waiting()).thenReturn(List.of());
    }

    private List<KindEnum> kinds() { return subject().compute().stream().map(RecommendationDto::getKind).toList(); }

    private void backupFolderMissing() {
        when(backups.hasChosenFolder()).thenReturn(false);
    }

    private static AiSentOrders.Sent sentOrder(AiRequestType type, Instant at) {
        return new AiSentOrders.Sent("lit-1", type, at, type.onPc(), true);
    }

    @Test
    void anAutoStartedPromotionIsAnnouncedOnlyOnItsDayAndWithoutButton() {
        // El 9 de nov SocialHub activó la promoción
        when(autoStarted.on(LocalDate.of(2026, 11, 9))).thenReturn(java.util.Set.of("lit-1"));
        var rows = subject().compute();
        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.getKind()).isEqualTo(KindEnum.PROMOTION_AUTO_STARTED);
        assertThat(row.getTitle()).isEqualTo("Promoción «Test inicial» activada sola: empieza hoy");
        assertThat(row.getTarget()).isNull();
        // Va al final, detrás del resto de filas
        backupFolderMissing();
        assertThat(kinds()).containsExactly(KindEnum.BACKUP_FOLDER_MISSING, KindEnum.PROMOTION_AUTO_STARTED);
        // Al día siguiente ya no sale
        now = madrid(11, 10, 0, 5);
        assertThat(kinds()).containsExactly(KindEnum.BACKUP_FOLDER_MISSING);
        // Si Raúl la activó a mano no hay anotación y no sale nunca
        now = madrid(11, 9, 11, 0);
        when(autoStarted.on(any())).thenReturn(java.util.Set.of());
        assertThat(kinds()).containsExactly(KindEnum.BACKUP_FOLDER_MISSING);
    }

    @Test
    void heldPublicationsWithoutPublerAccountGiveOneRowPerBrandAndNetworkWithAButtonToTheCard() {
        when(publer.missing()).thenReturn(List.of(new PublerAccountService.Missing("litoques", "TIKTOK", "TikTok", 2),
                new PublerAccountService.Missing("litoques", "INSTAGRAM", "Instagram", 1)));
        var rows = subject().compute();
        assertThat(rows).hasSize(2);
        var tiktok = rows.getFirst();
        assertThat(tiktok.getKind()).isEqualTo(KindEnum.PUBLER_ACCOUNT_MISSING);
        assertThat(tiktok.getTitle()).isEqualTo("Indica la cuenta de Publer de Litoques para TikTok");
        assertThat(tiktok.getDetail()).startsWith("2 publicaciones retenidas");
        assertThat(tiktok.getTarget().getScreen()).isEqualTo(ScreenEnum.PUBLER);
        assertThat(tiktok.getTarget().getBrandId()).isEqualTo("litoques");
        assertThat(rows.get(1).getTitle()).isEqualTo("Indica la cuenta de Publer de Litoques para Instagram");
        assertThat(rows.get(1).getDetail()).startsWith("1 publicación retenida");
        // Al indicar la cuenta ya no hay publicaciones retenidas por eso y la fila desaparece
        when(publer.missing()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
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
        assertThat(r.getPromotionId()).isEqualTo("lit-1");
        assertThat(r.getEtaText()).isNull();
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
        assertThat(r.getPromotionId()).isEqualTo("lit-1");

        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 1L));
        assertThat(subject().compute().getFirst().getTitle()).isEqualTo("Tienes 1 propuesta de «Test inicial» para revisar");
    }

    @Test
    void row3BrandWithoutAllItsFolders() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.BRAND_FOLDERS_MISSING);
        assertThat(r.getTitle()).isEqualTo("Crea las carpetas de Litoques");
        assertThat(r.getPromotionId()).isNull();
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.BRAND_FOLDERS);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void row4OrdersNotCollectedAfterThePickupPlusThirtyMinutes() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 30))));
        now = madrid(11, 9, 10, 9);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 9, 10, 10);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.ORDERS_NOT_COLLECTED);
        assertThat(r.getTitle()).isEqualTo("No ha llegado el resultado de los encargos de las 9:40. Revisa la tarea programada");
        assertThat(r.getTarget()).isNull();
    }

    @Test
    void row4CountsFiveMinutesOfSyncMarginBeforeThePickup() {
        // Enviado a las 9:36: con 5 minutos de sincronización no llega a la de las 9:40, le toca la de las 14:40
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 36))));
        now = madrid(11, 9, 10, 30);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 9, 15, 10);
        assertThat(subject().compute().getFirst().getTitle()).contains("de las 14:40");
    }

    @Test
    void row4IgnoresOrdersForTheLaterPickupAndTheOnesDoneOnThePc() {
        // Enviado a las 9:45: toca la de las 14:40, que aún no ha pasado
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 45))));
        now = madrid(11, 9, 11, 0);
        assertThat(subject().compute()).isEmpty();
        // Los del PC los recoge otra tarea
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.PUBLISH_CHECK, madrid(11, 9, 10, 0))));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row4IsNotShownWhenTheBrandOrdersFolderIsNotAvailable() {
        when(sent.waiting()).thenReturn(List.of(
                new AiSentOrders.Sent("lit-1", AiRequestType.CAPTIONS, madrid(11, 9, 9, 30), false, false)));
        now = madrid(11, 9, 11, 0);
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row4NamesTheMostRecentMissedPickup() {
        when(sent.waiting()).thenReturn(List.of(
                sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 30)),
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
    void row7BrandWithoutAnyPromotionAsksForTheFirstOne() {
        when(promotions.promotionRefs()).thenReturn(List.of());
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.NO_ACTIVE_PROMOTION);
        assertThat(r.getTitle()).isEqualTo("Da de alta la primera promoción de Litoques");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.NEW_PROMOTION);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
        assertThat(r.getTarget().getPromotionId()).isNull();
    }

    @Test
    void row7PausedPromotionAsksToActivateItAndOpensIt() {
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.NO_ACTIVE_PROMOTION);
        assertThat(r.getTitle()).isEqualTo("Activa una promoción de Litoques");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.NEW_PROMOTION);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
        assertThat(r.getTarget().getPromotionId()).isEqualTo("lit-1");
    }

    @Test
    void row7PlannedPromotionNotYetFinishedIsNotAskedToBeActivatedBecauseItActivatesItself() {
        // Empieza hoy (lunes 9), el lunes que viene o ya empezó: en todos los casos se activará sola
        for (LocalDate starts : List.of(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 1))) {
            when(promotions.promotionRefs()).thenReturn(List.of(ref("PLANNED", starts, LocalDate.of(2026, 12, 31))));
            assertThat(subject().compute()).isEmpty();
        }
    }

    @Test
    void row7PlannedPromotionAlreadyOverDoesNotCountAndAPausedOneStillAsksToActivate() {
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PLANNED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 8))));
        assertThat(subject().compute().getFirst().getTitle()).isEqualTo("Da de alta una promoción nueva de Litoques");
        // Una planificada por empezar y otra en pausa: no sale, la planificada se activará sola
        when(promotions.promotionRefs()).thenReturn(List.of(
                ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31)),
                new PromotionRef("lit-2", "litoques", "Otra", "PLANNED", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 31))));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row7OnlyFinishedPromotionsAskForANewOne() {
        when(promotions.promotionRefs()).thenReturn(List.of(ref("FINISHED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        var r = subject().compute().getFirst();
        assertThat(r.getTitle()).isEqualTo("Da de alta una promoción nueva de Litoques");
        assertThat(r.getTarget().getPromotionId()).isNull();
    }

    @Test
    void row8ProposalsOnTheWayGiveTheNextPickup() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 10, 50))));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.PROPOSALS_PENDING);
        assertThat(r.getTitle()).isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán sobre las 14:40");
        assertThat(r.getTarget()).isNull();
        assertThat(r.getPromotionId()).isEqualTo("lit-1");
        assertThat(r.getEtaText()).isEqualTo("sobre las 14:40");

        now = madrid(11, 9, 22, 0);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 21, 0))));
        assertThat(subject().compute().getFirst().getTitle())
                .isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán mañana sobre las 9:40");
        assertThat(subject().compute().getFirst().getEtaText()).isEqualTo("mañana sobre las 9:40");
        now = madrid(11, 9, 6, 0);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 8, 21, 0))));
        assertThat(subject().compute().getFirst().getTitle()).endsWith("Llegarán sobre las 9:40");
        // Enviado a las 14:37 (con los 5 min de sincronización no llega a la de las 14:40): le toca la de las 20:40
        now = madrid(11, 9, 14, 38);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 14, 37))));
        assertThat(subject().compute().getFirst().getEtaText()).isEqualTo("sobre las 20:40");
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
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 9, 30))));
        now = madrid(11, 9, 10, 30);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        // Con todo a la vez sale la lista completa, sin recortar
        assertThat(kinds()).containsExactly(KindEnum.HELD_PROPOSALS, KindEnum.PROPOSALS_TO_REVIEW, KindEnum.BRAND_FOLDERS_MISSING,
                KindEnum.ORDERS_NOT_COLLECTED, KindEnum.PUBLORA_MISSING, KindEnum.BACKUP_FOLDER_MISSING,
                KindEnum.PROPOSALS_PENDING);

        // Resueltas las tres primeras, desaparecen y quedan las demás por su orden
        when(held.current()).thenReturn(List.of());
        when(publishing.proposalCounts()).thenReturn(Map.of());
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(KindEnum.ORDERS_NOT_COLLECTED, KindEnum.PUBLORA_MISSING, KindEnum.BACKUP_FOLDER_MISSING,
                KindEnum.PROPOSALS_PENDING);
    }

    @Test
    void theListIsNotTrimmedAndEachSolvedRowDisappears() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        when(promotions.promotionRefs()).thenReturn(List.of());
        assertThat(kinds()).containsExactly(KindEnum.BRAND_FOLDERS_MISSING, KindEnum.PUBLORA_MISSING,
                KindEnum.BACKUP_FOLDER_MISSING, KindEnum.NO_ACTIVE_PROMOTION);

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
        // El DeLito tampoco tiene Publora ni promoción: cada fila es de su marca
        when(publora.hasAccounts("delito")).thenReturn(true);
        when(promotions.promotionRefs()).thenReturn(List.of(ref("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(subject().compute()).extracting(RecommendationDto::getId)
                .containsExactly("brand-folders:delito", "no-promotion:delito");
    }

    @Test
    void row9TheSpecWaitsForApprovedPublicationsOnceItWouldHaveBeenAsked() {
        // Lunes 9 de nov, 11:00: la semana en curso, sin publicaciones aprobadas
        var week = java.time.LocalDate.of(2026, 11, 9);
        when(promotions.activeBetween(week, week.plusDays(6))).thenReturn(List.of("lit-1"));
        when(publishing.approvedForAi("lit-1", week, week.plusDays(6))).thenReturn(List.of());
        var all = subject().compute();
        assertThat(all).hasSize(1);
        var row = all.getFirst();
        assertThat(row.getKind()).isEqualTo(KindEnum.SPEC_WAITING);
        assertThat(row.getTitle()).isEqualTo("La especificación de «Test inicial» espera a que apruebes publicaciones de la semana del 9 de nov");
        assertThat(row.getTarget()).isNull();
        // En cuanto hay una aprobada, desaparece
        when(publishing.approvedForAi("lit-1", week, week.plusDays(6))).thenReturn(List.of(Map.of("id", "x")));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row9DoesNotShowBeforeSundayNineAndLooksAtTheNextWeekAfterwards() {
        var thisWeek = java.time.LocalDate.of(2026, 11, 9);
        var nextWeek = thisWeek.plusWeeks(1);
        when(promotions.activeBetween(thisWeek, thisWeek.plusDays(6))).thenReturn(List.of("lit-1"));
        when(promotions.activeBetween(nextWeek, nextWeek.plusDays(6))).thenReturn(List.of("lit-1"));
        // Domingo 15 a las 8:00: todavía no tocaría pedirla
        now = madrid(11, 15, 8, 0);
        assertThat(subject().compute()).isEmpty();
        // Domingo 15 a las 9:00: la de la semana del 16, y va después de las filas de propuestas
        now = madrid(11, 15, 9, 0);
        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 2L));
        var all = subject().compute();
        assertThat(all.stream().map(RecommendationDto::getKind).toList())
                .containsExactly(KindEnum.PROPOSALS_TO_REVIEW, KindEnum.SPEC_WAITING);
        assertThat(all.getLast().getTitle()).endsWith("de la semana del 16 de nov");
    }

    // ------------------------------------------------------------------ Propuestas paradas por Drive o por el límite

    @Test
    void row1HeldProposalsAlsoWhenDriveIsNotAvailable() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DRIVE_UNAVAILABLE, 0)));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.HELD_PROPOSALS);
        assertThat(r.getId()).isEqualTo("held-proposals:lit-1");
        assertThat(r.getTitle()).isEqualTo("Las propuestas de «Test inicial» no pueden salir");
        assertThat(r.getDetail()).isEqualTo("La carpeta de Encargos no está disponible en este ordenador. ¿Está abierto Google Drive?");
        assertThat(r.getTarget()).isNull();
        assertThat(r.getPromotionId()).isEqualTo("lit-1");
        // Al volver Drive, el dispatcher ya no lo anota y la fila desaparece
        when(held.stalledProposals()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row1HeldProposalsAlsoWhenTheDailyLimitIsReachedWithAButtonToClaude() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DAILY_LIMIT, 10)));
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.HELD_PROPOSALS);
        assertThat(r.getDetail()).isEqualTo("Límite diario de encargos a Claude alcanzado (10): salen mañana");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.CLAUDE);
        assertThat(r.getTarget().getBrandId()).isNull();
    }

    @Test
    void onlyOneHeldRowPerPromotionWithHeldFirstThenDriveThenLimit() {
        var limit = new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DAILY_LIMIT, 10);
        var drive = new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DRIVE_UNAVAILABLE, 0);
        when(held.stalledProposals()).thenReturn(List.of(limit, drive));
        var all = subject().compute();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getDetail()).startsWith("La carpeta de Encargos no está disponible");

        when(held.current()).thenReturn(List.of(new AiHeldOrders.Held("t1", "est", "P", "La marca no tiene carpeta de «Encargos».",
                "lit-1", AiRequestType.WEEKLY_PROPOSALS)));
        all = subject().compute();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getDetail()).isEqualTo("La marca no tiene carpeta de «Encargos».");
        assertThat(all.getFirst().getTarget().getScreen()).isEqualTo(ScreenEnum.BRAND_FOLDERS);
    }

    @Test
    void stalledProposalsOfAnUnknownPromotionAreIgnored() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("otra", AiHeldOrders.Cause.DAILY_LIMIT, 10)));
        assertThat(subject().compute()).isEmpty();
    }

    // ------------------------------------------------------------------ Avisar si falta algo para publicar

    private static PublishingQueries.Unroutable unroutable(String platform, PubloraService.Gap gap, LocalDate on) {
        return new PublishingQueries.Unroutable("lit-1", on, platform, gap);
    }

    private void pcSilent() {
        // Enviado el lunes a las 9:00 y ahora son las 11:00: 120 minutos de horario sin respuesta
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.PUBLER_SCHEDULE, madrid(11, 9, 9, 0))));
    }

    private void unroutableThisWeek(PubloraService.Gap gap) {
        when(publishing.withoutPubloraRoute(eq(LocalDate.of(2026, 11, 9)), eq(LocalDate.of(2026, 11, 16)), any()))
                .thenReturn(List.of(unroutable("Instagram", gap, LocalDate.of(2026, 11, 12))));
    }

    @Test
    void theSilentPcTaskGivesARowWithoutButtonAndDisappearsWhenAResultArrives() {
        pcSilent();
        var all = subject().compute();
        assertThat(all).hasSize(1);
        var r = all.getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.PC_TASK_SILENT);
        assertThat(r.getTitle()).isEqualTo("La tarea «Publicar con Chrome» no contesta");
        assertThat(r.getDetail()).isEqualTo("1 encargo sin contestar desde las 9:00. ¿Está activa la tarea «Publicar con Chrome» y abierto Chrome?");
        assertThat(r.getTarget()).isNull();
        // Llega el resultado: el encargo ya no espera
        when(sent.waiting()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
        // Uno enviado hace menos de 90 minutos no avisa
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.PUBLISH_CHECK, madrid(11, 9, 9, 45))));
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void noPublishingRouteWhenThePcIsSilentAndPubloraHasNoKey() {
        pcSilent();
        unroutableThisWeek(PubloraService.Gap.NO_KEY);
        var all = subject().compute();
        // Va la primera, antes que la tarea del PC
        assertThat(all.stream().map(RecommendationDto::getKind).toList()).containsExactly(KindEnum.NO_PUBLISHING_ROUTE, KindEnum.PC_TASK_SILENT);
        var r = all.getFirst();
        assertThat(r.getId()).isEqualTo("no-publishing-route:litoques");
        assertThat(r.getTitle()).isEqualTo("No hay forma de publicar Litoques");
        assertThat(r.getDetail()).contains("1 publicación de los próximos 7 días (la primera, el 12 de nov)")
                .contains("la tarea «Publicar con Chrome» no contesta").contains("Publora no tiene clave");
        assertThat(r.getTarget().getScreen()).isEqualTo(ScreenEnum.PUBLORA);
        assertThat(r.getTarget().getBrandId()).isEqualTo("litoques");
    }

    @Test
    void noPublishingRouteDisappearsWithAnUsablePubloraOrAResultFromThePc() {
        pcSilent();
        unroutableThisWeek(PubloraService.Gap.NO_ACCOUNT);
        assertThat(subject().compute().getFirst().getDetail()).contains("Publora no tiene cuenta de Instagram");
        // Se pone la clave y la cuenta: Publora ya puede publicarlas y no queda ninguna sin vía
        when(publishing.withoutPubloraRoute(any(), any(), any())).thenReturn(List.of());
        assertThat(kinds()).containsExactly(KindEnum.PC_TASK_SILENT);
        // O la tarea del PC contesta
        unroutableThisWeek(PubloraService.Gap.NO_KEY);
        when(sent.waiting()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void noPublishingRouteHasNoButtonWhenTheBrandDismissedPublora() {
        pcSilent();
        unroutableThisWeek(PubloraService.Gap.DISMISSED);
        var r = subject().compute().getFirst();
        assertThat(r.getKind()).isEqualTo(KindEnum.NO_PUBLISHING_ROUTE);
        assertThat(r.getDetail()).contains("«No uso Publora»");
        assertThat(r.getTarget()).isNull();
    }

    @Test
    void noPublishingRouteCountsAndDatesTheFirstOfSeveralPublications() {
        pcSilent();
        when(publishing.withoutPubloraRoute(any(), any(), any())).thenReturn(List.of(
                unroutable("TikTok", PubloraService.Gap.NO_ACCOUNT, LocalDate.of(2026, 11, 14)),
                unroutable("Instagram", PubloraService.Gap.NO_ACCOUNT, LocalDate.of(2026, 11, 11))));
        var r = subject().compute().getFirst();
        assertThat(r.getDetail()).startsWith("2 publicaciones de los próximos 7 días (la primera, el 11 de nov)")
                .contains("Publora no tiene cuenta de Instagram");
    }

    @Test
    void noPublishingRouteNeedsTheSilentPcTask() {
        unroutableThisWeek(PubloraService.Gap.NO_KEY);
        assertThat(subject().compute()).isEmpty();
    }
}
