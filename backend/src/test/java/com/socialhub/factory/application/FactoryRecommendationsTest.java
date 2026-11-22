package com.socialhub.factory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudePickups;
import com.socialhub.backup.Backups;
import com.socialhub.factory.domain.Recommendation;
import com.socialhub.factory.domain.Recommendation.Kind;
import com.socialhub.factory.domain.Recommendation.Screen;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionQueries.BrandRef;
import com.socialhub.promotion.PromotionQueries.PromotionRef;
import com.socialhub.publishing.PublerAccounts;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraConnection;
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
    private final PubloraConnection publora = mock(PubloraConnection.class);
    private final PublerAccounts publer = mock(PublerAccounts.class);
    private final com.socialhub.ai.AiAutoStarted autoStarted = mock(com.socialhub.ai.AiAutoStarted.class);
    private final Backups backups = mock(Backups.class);
    private final com.socialhub.pieces.ContentGaps contentGaps = mock(com.socialhub.pieces.ContentGaps.class);
    private final com.socialhub.pieces.MissingMaterial missingMaterial = mock(com.socialhub.pieces.MissingMaterial.class);
    private final com.socialhub.publishing.InstagramOAuth instagramOAuth = mock(com.socialhub.publishing.InstagramOAuth.class);

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
                contentGaps, missingMaterial, instagramOAuth, Clock.fixed(now, ClaudePickups.ZONE),
                com.socialhub.settings.FixedMargin.of(com.socialhub.settings.FixedMargin.SIX_HOURS));
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
        when(missingMaterial.of(any())).thenReturn(new com.socialhub.pieces.MissingMaterial.Report(true, 0, List.of(), null, ""));
    }

    private List<Kind> kinds() { return subject().compute().stream().map(Recommendation::kind).toList(); }

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
        assertThat(row.kind()).isEqualTo(Kind.PROMOTION_AUTO_STARTED);
        assertThat(row.title()).isEqualTo("Promoción «Test inicial» activada sola: empieza hoy");
        assertThat(row.target()).isNull();
        // Va al final, detrás del resto de filas
        backupFolderMissing();
        assertThat(kinds()).containsExactly(Kind.BACKUP_FOLDER_MISSING, Kind.PROMOTION_AUTO_STARTED);
        // Al día siguiente ya no sale
        now = madrid(11, 10, 0, 5);
        assertThat(kinds()).containsExactly(Kind.BACKUP_FOLDER_MISSING);
        // Si Raúl la activó a mano no hay anotación y no sale nunca
        now = madrid(11, 9, 11, 0);
        when(autoStarted.on(any())).thenReturn(java.util.Set.of());
        assertThat(kinds()).containsExactly(Kind.BACKUP_FOLDER_MISSING);
    }

    @Test
    void heldPublicationsWithoutPublerAccountGiveOneRowPerBrandAndNetworkWithAButtonToTheCard() {
        when(publer.missing()).thenReturn(List.of(new PublerAccounts.Missing("litoques", "TIKTOK", "TikTok", 2),
                new PublerAccounts.Missing("litoques", "INSTAGRAM", "Instagram", 1)));
        var rows = subject().compute();
        assertThat(rows).hasSize(2);
        var tiktok = rows.getFirst();
        assertThat(tiktok.kind()).isEqualTo(Kind.PUBLER_ACCOUNT_MISSING);
        assertThat(tiktok.title()).isEqualTo("Indica la cuenta de Publer de Litoques para TikTok");
        assertThat(tiktok.detail()).startsWith("2 publicaciones retenidas");
        assertThat(tiktok.target().screen()).isEqualTo(Screen.PUBLER);
        assertThat(tiktok.target().brandId()).isEqualTo("litoques");
        assertThat(rows.get(1).title()).isEqualTo("Indica la cuenta de Publer de Litoques para Instagram");
        assertThat(rows.get(1).detail()).startsWith("1 publicación retenida");
        // Al indicar la cuenta ya no hay publicaciones retenidas por eso y la fila desaparece
        when(publer.missing()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void anActiveBrandWithoutPublerAccountGetsTheRowEvenIfNothingIsHeldAndItMergesWithHeldOnes() {
        when(publer.unset("litoques")).thenReturn(List.of(new PublerAccounts.Missing("litoques", "INSTAGRAM", "Instagram", 0),
                new PublerAccounts.Missing("litoques", "TIKTOK", "TikTok", 0)));
        var rows = subject().compute();
        assertThat(rows).extracting(Recommendation::title).containsExactly(
                "Indica la cuenta de Publer de Litoques para Instagram", "Indica la cuenta de Publer de Litoques para TikTok");
        assertThat(rows.getFirst().detail()).startsWith("Sin la cuenta");
        assertThat(rows.getFirst().target().screen()).isEqualTo(Screen.PUBLER);
        // Con publicaciones retenidas de TikTok, la fila de TikTok lo dice y no se duplica
        when(publer.missing()).thenReturn(List.of(new PublerAccounts.Missing("litoques", "TIKTOK", "TikTok", 2)));
        rows = subject().compute();
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> r.title().endsWith("TikTok")).findFirst().orElseThrow().detail()).startsWith("2 publicaciones retenidas");
        // Sin promoción activa y sin retenidas no hay fila; al guardar las cuentas desaparece
        when(publer.missing()).thenReturn(List.of());
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(subject().compute().stream().filter(r -> r.kind() == Kind.PUBLER_ACCOUNT_MISSING)).isEmpty();
        when(promotions.promotionRefs()).thenReturn(List.of(ref("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        when(publer.unset("litoques")).thenReturn(List.of());
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
        assertThat(r.kind()).isEqualTo(Kind.HELD_PROPOSALS);
        assertThat(r.id()).isEqualTo("held-proposals:lit-1");
        assertThat(r.title()).isEqualTo("Las propuestas de «Test inicial» no pueden salir");
        assertThat(r.detail()).isEqualTo("La marca no tiene carpeta de «Encargos».");
        assertThat(r.target().screen()).isEqualTo(Screen.BRAND_FOLDERS);
        assertThat(r.target().brandId()).isEqualTo("litoques");
        assertThat(r.promotionId()).isEqualTo("lit-1");
        assertThat(r.etaText()).isNull();
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
        assertThat(r.kind()).isEqualTo(Kind.PROPOSALS_TO_REVIEW);
        assertThat(r.title()).isEqualTo("Tienes 4 propuestas de «Test inicial» para revisar");
        assertThat(r.target().screen()).isEqualTo(Screen.PROPOSALS);
        assertThat(r.target().promotionId()).isEqualTo("lit-1");
        assertThat(r.target().brandId()).isEqualTo("litoques");
        assertThat(r.promotionId()).isEqualTo("lit-1");

        when(publishing.proposalCounts()).thenReturn(Map.of("lit-1", 1L));
        assertThat(subject().compute().getFirst().title()).isEqualTo("Tienes 1 propuesta de «Test inicial» para revisar");
    }

    @Test
    void row3BrandWithoutAllItsFolders() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.BRAND_FOLDERS_MISSING);
        assertThat(r.title()).isEqualTo("Crea las carpetas de Litoques");
        assertThat(r.promotionId()).isNull();
        assertThat(r.target().screen()).isEqualTo(Screen.BRAND_FOLDERS);
        assertThat(r.target().brandId()).isEqualTo("litoques");
    }

    @Test
    void row4OrdersNotCollectedAfterThePickupPlusThirtyMinutes() {
        // Sin prisa: solo lo recogen las pasadas completas, así que a las 9:30 le toca la de las 20:30
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 9, 30))));
        now = madrid(11, 9, 20, 59);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 9, 21, 0);
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.ORDERS_NOT_COLLECTED);
        assertThat(r.title()).isEqualTo("No ha llegado el resultado de los encargos de las 20:30. Revisa la tarea programada");
        assertThat(r.target()).isNull();
    }

    @Test
    void row4AnUrgentOrderIsExpectedAtTheNextPassWhateverItIs() {
        when(sent.waiting()).thenReturn(List.of(
                new AiSentOrders.Sent("lit-1", AiRequestType.CAPTIONS, madrid(11, 9, 9, 30), false, true, true)));
        now = madrid(11, 9, 10, 59);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 9, 11, 0);
        assertThat(subject().compute().getFirst().title()).contains("de las 10:30");
    }

    @Test
    void row4CountsFiveMinutesOfSyncMarginBeforeThePickup() {
        // Enviado a las 20:26: con 5 minutos de sincronización no llega a la de las 20:30, le toca la de las 0:30
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 20, 26))));
        now = madrid(11, 9, 22, 30);
        assertThat(subject().compute()).isEmpty();
        now = madrid(11, 10, 1, 10);
        assertThat(subject().compute().getFirst().title()).contains("de las 0:30");
    }

    @Test
    void row4IgnoresOrdersForTheLaterPickupAndTheOnesDoneOnThePc() {
        // Enviado a las 9:45: toca la de las 20:30, que aún no ha pasado
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
                sentOrder(AiRequestType.CAPTIONS, madrid(11, 9, 21, 0))));
        now = madrid(11, 10, 2, 0);
        assertThat(subject().compute().getFirst().title()).contains("de las 0:30");
    }

    @Test
    void row5BrandWithoutPublora() {
        when(publora.hasAccounts("litoques")).thenReturn(false);
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.PUBLORA_MISSING);
        assertThat(r.title()).isEqualTo("Conecta Publora para Litoques");
        assertThat(r.target().screen()).isEqualTo(Screen.PUBLORA);
        assertThat(r.target().brandId()).isEqualTo("litoques");
    }

    @Test
    void row5IsNotShownWhenTheBrandDismissedPublora() {
        when(publora.hasAccounts("litoques")).thenReturn(false);
        when(publora.isDismissed("litoques")).thenReturn(true);
        assertThat(subject().compute()).isEmpty();
        // Al reactivarla vuelve a salir
        when(publora.isDismissed("litoques")).thenReturn(false);
        assertThat(kinds()).containsExactly(Kind.PUBLORA_MISSING);
    }

    @Test
    void row6NoBackupFolder() {
        backupFolderMissing();
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.BACKUP_FOLDER_MISSING);
        assertThat(r.title()).isEqualTo("Elige dónde guardar las copias");
        assertThat(r.target().screen()).isEqualTo(Screen.BACKUPS);
    }

    @Test
    void row7BrandWithoutAnyPromotionAsksForTheFirstOne() {
        when(promotions.promotionRefs()).thenReturn(List.of());
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.NO_ACTIVE_PROMOTION);
        assertThat(r.title()).isEqualTo("Da de alta la primera promoción de Litoques");
        assertThat(r.target().screen()).isEqualTo(Screen.NEW_PROMOTION);
        assertThat(r.target().brandId()).isEqualTo("litoques");
        assertThat(r.target().promotionId()).isNull();
    }

    @Test
    void row7PausedPromotionAsksToActivateItAndOpensIt() {
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.NO_ACTIVE_PROMOTION);
        assertThat(r.title()).isEqualTo("Activa una promoción de Litoques");
        assertThat(r.target().screen()).isEqualTo(Screen.NEW_PROMOTION);
        assertThat(r.target().brandId()).isEqualTo("litoques");
        assertThat(r.target().promotionId()).isEqualTo("lit-1");
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
        assertThat(subject().compute().getFirst().title()).isEqualTo("Da de alta una promoción nueva de Litoques");
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
        assertThat(r.title()).isEqualTo("Da de alta una promoción nueva de Litoques");
        assertThat(r.target().promotionId()).isNull();
    }

    @Test
    void aPublicationWithoutContentThatLeavesInLessThanThreeDaysAsksToRequestItNow() {
        var close = new com.socialhub.pieces.ContentGaps.Gap("2026-11-11-instagram", "Pregunta grande", madrid(11, 11, 13, 0));
        var far = new com.socialhub.pieces.ContentGaps.Gap("2026-11-20-instagram", "Otra", madrid(11, 20, 13, 0));
        when(contentGaps.of("lit-1")).thenReturn(List.of(close, far));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.CONTENT_MISSING);
        assertThat(r.title()).isEqualTo("Pregunta grande no tiene contenido");
        assertThat(r.detail()).contains("Sale el 11 de nov a las 13:00").contains("Pedir contenido ya").contains("las 2 publicaciones");
        assertThat(r.promotionId()).isEqualTo("lit-1");
        assertThat(r.publicationIds()).containsExactly("2026-11-11-instagram", "2026-11-20-instagram");
        assertThat(r.target()).isNull();
        // Si la más próxima sale dentro de 3 días o más, no hay fila
        when(contentGaps.of("lit-1")).thenReturn(List.of(far));
        assertThat(subject().compute()).isEmpty();
        // Una promoción pausada tampoco
        when(contentGaps.of("lit-1")).thenReturn(List.of(close));
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(subject().compute().stream().map(x -> x.kind())).doesNotContain(Kind.CONTENT_MISSING);
    }

    @Test
    void aBrandWithAnActivePromotionAndAnIncompleteIdentityAsksForIt() {
        when(promotions.identityGaps("litoques")).thenReturn(List.of("el tono", "el público"));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.IDENTITY_MISSING);
        assertThat(r.title()).isEqualTo("Cuéntale a Claude cómo es Litoques");
        assertThat(r.detail()).contains("Falta el tono, el público").contains("importarlo desde un .txt");
        assertThat(r.target().screen()).isEqualTo(Screen.BRAND_IDENTITY);
        assertThat(r.target().brandId()).isEqualTo("litoques");
        // Sin promoción en marcha no hace falta todavía
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(kinds()).doesNotContain(Kind.IDENTITY_MISSING);
    }

    @Test
    void aPublicationOnItsWayWithoutSixHoursOfMarginAsksToChangeItsTime() {
        var soon = new PublishingQueries.WithoutMargin("lit-1", "2026-11-09-instagram", "Poema 1", madrid(11, 9, 14, 0));
        when(publishing.withoutMargin(now)).thenReturn(List.of(soon));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.PUBLICATION_TOO_SOON);
        assertThat(r.title()).isEqualTo("«Poema 1» ya no deja 6 horas de margen");
        assertThat(r.detail()).contains("sale el 9 de nov a las 14:00").contains("aún no está programada");
        assertThat(r.target().screen()).isEqualTo(Screen.PUBLICATIONS);
        assertThat(r.target().promotionId()).isEqualTo("lit-1");
        // Si su hora ya pasó, lo dice
        when(publishing.withoutMargin(now)).thenReturn(List.of(
                new PublishingQueries.WithoutMargin("lit-1", "x", "Poema 1", madrid(11, 8, 19, 0))));
        assertThat(subject().compute().getFirst().detail()).contains("ya pasó");
        // Una promoción pausada no avisa
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(kinds()).doesNotContain(Kind.PUBLICATION_TOO_SOON);
    }

    @Test
    void row8AnUrgentOrderArrivesAtTheNextPassAndOneWithoutRushAtTheNightOne() {
        now = madrid(11, 9, 9, 40);
        when(sent.waiting()).thenReturn(List.of(
                new AiSentOrders.Sent("lit-1", AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 9, 30), false, true, true)));
        assertThat(subject().compute().getFirst().etaText()).isEqualTo("sobre las 10:30");
        when(sent.waiting()).thenReturn(List.of(
                new AiSentOrders.Sent("lit-1", AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 9, 30), false, true, false)));
        assertThat(subject().compute().getFirst().etaText()).isEqualTo("sobre las 20:30");
    }

    @Test
    void row8ProposalsOnTheWayGiveTheNextPickup() {
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 10, 50))));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.PROPOSALS_PENDING);
        assertThat(r.title()).isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán sobre las 20:30");
        assertThat(r.target()).isNull();
        assertThat(r.promotionId()).isEqualTo("lit-1");
        assertThat(r.etaText()).isEqualTo("sobre las 20:30");

        now = madrid(11, 9, 22, 0);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 21, 0))));
        assertThat(subject().compute().getFirst().title())
                .isEqualTo("Propuestas de «Test inicial» pedidas. Llegarán mañana sobre las 0:30");
        assertThat(subject().compute().getFirst().etaText()).isEqualTo("mañana sobre las 0:30");
        now = madrid(11, 9, 0, 10);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 8, 21, 0))));
        assertThat(subject().compute().getFirst().title()).endsWith("Llegarán sobre las 0:30");
        // Enviado a las 20:26 (con los 5 min de sincronización no llega a la de las 20:30): le toca la de las 0:30
        now = madrid(11, 9, 20, 27);
        when(sent.waiting()).thenReturn(List.of(sentOrder(AiRequestType.WEEKLY_PROPOSALS, madrid(11, 9, 20, 26))));
        assertThat(subject().compute().getFirst().etaText()).isEqualTo("mañana sobre las 0:30");
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
        now = madrid(11, 9, 21, 0);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        // Con todo a la vez sale la lista completa, sin recortar
        assertThat(kinds()).containsExactly(Kind.HELD_PROPOSALS, Kind.PROPOSALS_TO_REVIEW, Kind.BRAND_FOLDERS_MISSING,
                Kind.ORDERS_NOT_COLLECTED, Kind.PUBLORA_MISSING, Kind.BACKUP_FOLDER_MISSING,
                Kind.PROPOSALS_PENDING);

        // Resueltas las tres primeras, desaparecen y quedan las demás por su orden
        when(held.current()).thenReturn(List.of());
        when(publishing.proposalCounts()).thenReturn(Map.of());
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(Kind.ORDERS_NOT_COLLECTED, Kind.PUBLORA_MISSING, Kind.BACKUP_FOLDER_MISSING,
                Kind.PROPOSALS_PENDING);
    }

    @Test
    void theListIsNotTrimmedAndEachSolvedRowDisappears() {
        when(promotions.hasAllBrandFolders("litoques")).thenReturn(false);
        when(publora.hasAccounts("litoques")).thenReturn(false);
        backupFolderMissing();
        when(promotions.promotionRefs()).thenReturn(List.of());
        assertThat(kinds()).containsExactly(Kind.BRAND_FOLDERS_MISSING, Kind.PUBLORA_MISSING,
                Kind.BACKUP_FOLDER_MISSING, Kind.NO_ACTIVE_PROMOTION);

        when(promotions.hasAllBrandFolders("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(Kind.PUBLORA_MISSING, Kind.BACKUP_FOLDER_MISSING, Kind.NO_ACTIVE_PROMOTION);

        when(publora.hasAccounts("litoques")).thenReturn(true);
        assertThat(kinds()).containsExactly(Kind.BACKUP_FOLDER_MISSING, Kind.NO_ACTIVE_PROMOTION);
    }

    @Test
    void severalBrandsGiveOneRowEach() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("litoques", "Litoques"), new BrandRef("delito", "El DeLito")));
        when(promotions.hasAllBrandFolders("delito")).thenReturn(false);
        var all = subject().compute();
        assertThat(all).extracting(Recommendation::id).contains("brand-folders:delito");
        // El DeLito tampoco tiene Publora ni promoción: cada fila es de su marca
        when(publora.hasAccounts("delito")).thenReturn(true);
        when(promotions.promotionRefs()).thenReturn(List.of(ref("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(subject().compute()).extracting(Recommendation::id)
                .containsExactly("brand-folders:delito", "no-promotion:delito");
    }

    // ------------------------------------------------------------------ Propuestas paradas por Drive o por el límite

    @Test
    void row1HeldProposalsAlsoWhenDriveIsNotAvailable() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DRIVE_UNAVAILABLE, 0)));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.HELD_PROPOSALS);
        assertThat(r.id()).isEqualTo("held-proposals:lit-1");
        assertThat(r.title()).isEqualTo("Las propuestas de «Test inicial» no pueden salir");
        assertThat(r.detail()).isEqualTo("La carpeta de Encargos no está disponible en este ordenador. ¿Está abierto Google Drive?");
        assertThat(r.target()).isNull();
        assertThat(r.promotionId()).isEqualTo("lit-1");
        // Al volver Drive, el dispatcher ya no lo anota y la fila desaparece
        when(held.stalledProposals()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void row1HeldProposalsAlsoWhenTheDailyLimitIsReachedWithAButtonToClaude() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DAILY_LIMIT, 10)));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.HELD_PROPOSALS);
        assertThat(r.detail()).isEqualTo("Límite diario de encargos a Claude alcanzado (10): salen mañana");
        assertThat(r.target().screen()).isEqualTo(Screen.CLAUDE);
        assertThat(r.target().brandId()).isNull();
    }

    @Test
    void onlyOneHeldRowPerPromotionWithHeldFirstThenDriveThenLimit() {
        var limit = new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DAILY_LIMIT, 10);
        var drive = new AiHeldOrders.Stalled("lit-1", AiHeldOrders.Cause.DRIVE_UNAVAILABLE, 0);
        when(held.stalledProposals()).thenReturn(List.of(limit, drive));
        var all = subject().compute();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().detail()).startsWith("La carpeta de Encargos no está disponible");

        when(held.current()).thenReturn(List.of(new AiHeldOrders.Held("t1", "est", "P", "La marca no tiene carpeta de «Encargos».",
                "lit-1", AiRequestType.WEEKLY_PROPOSALS)));
        all = subject().compute();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().detail()).isEqualTo("La marca no tiene carpeta de «Encargos».");
        assertThat(all.getFirst().target().screen()).isEqualTo(Screen.BRAND_FOLDERS);
    }

    @Test
    void stalledProposalsOfAnUnknownPromotionAreIgnored() {
        when(held.stalledProposals()).thenReturn(List.of(new AiHeldOrders.Stalled("otra", AiHeldOrders.Cause.DAILY_LIMIT, 10)));
        assertThat(subject().compute()).isEmpty();
    }

    // ------------------------------------------------------------------ Avisar si falta algo para publicar

    private static PublishingQueries.Unroutable unroutable(String platform, PubloraConnection.Gap gap, LocalDate on) {
        return new PublishingQueries.Unroutable("lit-1", on, platform, gap);
    }

    private void pcSilent() {
        // Urgente enviado el lunes a las 9:00 (le tocaba la pasada de las 10:50) y ahora son las 12:30: más de 90 minutos tarde
        now = madrid(11, 9, 12, 30);
        when(sent.waiting()).thenReturn(List.of(
                new AiSentOrders.Sent("lit-1", AiRequestType.PUBLER_SCHEDULE, madrid(11, 9, 9, 0), true, true, true)));
    }

    private void unroutableThisWeek(PubloraConnection.Gap gap) {
        when(publishing.withoutPubloraRoute(eq(LocalDate.of(2026, 11, 9)), eq(LocalDate.of(2026, 11, 16)), any()))
                .thenReturn(List.of(unroutable("Instagram", gap, LocalDate.of(2026, 11, 12))));
    }

    @Test
    void theSilentPcTaskGivesARowWithoutButtonAndDisappearsWhenAResultArrives() {
        pcSilent();
        var all = subject().compute();
        assertThat(all).hasSize(1);
        var r = all.getFirst();
        assertThat(r.kind()).isEqualTo(Kind.PC_TASK_SILENT);
        assertThat(r.title()).isEqualTo("La tarea «Publicar con Chrome» no contesta");
        assertThat(r.detail()).isEqualTo("1 encargo sin contestar desde las 9:00. ¿Está activa la tarea «Publicar con Chrome» y abierto Chrome?");
        assertThat(r.target()).isNull();
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
        unroutableThisWeek(PubloraConnection.Gap.NO_KEY);
        var all = subject().compute();
        // Va la primera, antes que la tarea del PC
        assertThat(all.stream().map(Recommendation::kind).toList()).containsExactly(Kind.NO_PUBLISHING_ROUTE, Kind.PC_TASK_SILENT);
        var r = all.getFirst();
        assertThat(r.id()).isEqualTo("no-publishing-route:litoques");
        assertThat(r.title()).isEqualTo("No hay forma de publicar Litoques");
        assertThat(r.detail()).contains("1 publicación de los próximos 7 días (la primera, el 12 de nov)")
                .contains("la tarea «Publicar con Chrome» no contesta").contains("Publora no tiene clave");
        assertThat(r.target().screen()).isEqualTo(Screen.PUBLORA);
        assertThat(r.target().brandId()).isEqualTo("litoques");
    }

    @Test
    void noPublishingRouteDisappearsWithAnUsablePubloraOrAResultFromThePc() {
        pcSilent();
        unroutableThisWeek(PubloraConnection.Gap.NO_ACCOUNT);
        assertThat(subject().compute().getFirst().detail()).contains("Publora no tiene cuenta de Instagram");
        // Se pone la clave y la cuenta: Publora ya puede publicarlas y no queda ninguna sin vía
        when(publishing.withoutPubloraRoute(any(), any(), any())).thenReturn(List.of());
        assertThat(kinds()).containsExactly(Kind.PC_TASK_SILENT);
        // O la tarea del PC contesta
        unroutableThisWeek(PubloraConnection.Gap.NO_KEY);
        when(sent.waiting()).thenReturn(List.of());
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void noPublishingRouteHasNoButtonWhenTheBrandDismissedPublora() {
        pcSilent();
        unroutableThisWeek(PubloraConnection.Gap.DISMISSED);
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.NO_PUBLISHING_ROUTE);
        assertThat(r.detail()).contains("«No uso Publora»");
        assertThat(r.target()).isNull();
    }

    @Test
    void noPublishingRouteCountsAndDatesTheFirstOfSeveralPublications() {
        pcSilent();
        when(publishing.withoutPubloraRoute(any(), any(), any())).thenReturn(List.of(
                unroutable("TikTok", PubloraConnection.Gap.NO_ACCOUNT, LocalDate.of(2026, 11, 14)),
                unroutable("Instagram", PubloraConnection.Gap.NO_ACCOUNT, LocalDate.of(2026, 11, 11))));
        var r = subject().compute().getFirst();
        assertThat(r.detail()).startsWith("2 publicaciones de los próximos 7 días (la primera, el 11 de nov)")
                .contains("Publora no tiene cuenta de Instagram");
    }

    @Test
    void noPublishingRouteNeedsTheSilentPcTask() {
        unroutableThisWeek(PubloraConnection.Gap.NO_KEY);
        assertThat(subject().compute()).isEmpty();
    }

    @Test
    void instagramConnectionExpiringSoonAsksToRenewItWithAButtonToTheCard() {
        when(instagramOAuth.notice()).thenReturn(java.util.Optional.of(new com.socialhub.publishing.InstagramOAuth.Notice(false, 6, false)));
        var r = subject().compute().getFirst();
        assertThat(r.kind()).isEqualTo(Kind.INSTAGRAM_CONNECTION_EXPIRING);
        assertThat(r.title()).isEqualTo("La conexión con Instagram caduca en 6 días");
        assertThat(r.detail()).contains("Renovar conexión");
        assertThat(r.target().screen()).isEqualTo(Screen.INSTAGRAM);
    }

    @Test
    void anExpiredInstagramConnectionSaysSoAndMentionsTheSystemKeyFallback() {
        when(instagramOAuth.notice()).thenReturn(java.util.Optional.of(new com.socialhub.publishing.InstagramOAuth.Notice(true, 0, true)));
        var r = subject().compute().getFirst();
        assertThat(r.title()).isEqualTo("La conexión con Instagram ha caducado");
        assertThat(r.detail()).contains("clave de usuario del sistema");
    }

    @Test
    void withoutAConnectionOrWithEnoughDaysThereIsNoInstagramNotice() {
        assertThat(kinds()).doesNotContain(Kind.INSTAGRAM_CONNECTION_EXPIRING);
    }

    @Test
    void missingMaterialIsOnlyANoticeThatNamesWhatFallbackWillBeUsed() {
        var report = new com.socialhub.pieces.MissingMaterial.Report(true, 3, List.of("Con púa", "Pregunta grande"), null, "");
        when(missingMaterial.of("lit-1")).thenReturn(report);
        var r = subject().compute().stream().filter(x -> x.kind() == Kind.MATERIAL_MISSING).findFirst().orElseThrow();
        assertThat(r.title()).isEqualTo("Faltan imágenes o vídeos para: Con púa, Pregunta grande");
        assertThat(r.detail()).contains("fondo con el color de la marca").contains("portada.jpg").contains("No bloquea nada");
        assertThat(r.target().screen()).isEqualTo(Screen.BRAND_FOLDERS);
        // Con portada, dice cuál usará; con estilo visual, lo menciona
        when(missingMaterial.of("lit-1")).thenReturn(
                new com.socialhub.pieces.MissingMaterial.Report(true, 1, List.of("Con púa"), "portada.png", "Navidad: rojo y oro"));
        var withCover = subject().compute().stream().filter(x -> x.kind() == Kind.MATERIAL_MISSING).findFirst().orElseThrow();
        assertThat(withCover.detail()).contains("la portada de la marca («portada.png»)").contains("Navidad: rojo y oro");
        // Nada que avisar: sin publicaciones pendientes o con todo el material
        when(missingMaterial.of("lit-1")).thenReturn(new com.socialhub.pieces.MissingMaterial.Report(true, 2, List.of(), null, ""));
        assertThat(kinds()).doesNotContain(Kind.MATERIAL_MISSING);
    }

    @Test
    void missingMaterialNamesAtMostFiveTitlesAndSaysWhenTheFolderIsNotAvailable() {
        var titles = List.of("A uno", "B dos", "C tres", "D cuatro", "E cinco", "F seis", "G siete");
        when(missingMaterial.of("lit-1")).thenReturn(new com.socialhub.pieces.MissingMaterial.Report(true, 7, titles, null, ""));
        var many = subject().compute().stream().filter(x -> x.kind() == Kind.MATERIAL_MISSING).findFirst().orElseThrow();
        assertThat(many.title()).endsWith("E cinco y 2 más").doesNotContain("F seis");
        // Carpeta no accesible: aviso claro, sin fallar
        when(missingMaterial.of("lit-1")).thenReturn(new com.socialhub.pieces.MissingMaterial.Report(false, 2, List.of(), null, ""));
        var offline = subject().compute().stream().filter(x -> x.kind() == Kind.MATERIAL_MISSING).findFirst().orElseThrow();
        assertThat(offline.title()).startsWith("No se puede comprobar el material");
        assertThat(offline.detail()).contains("Google Drive").contains("solo un aviso");
        // Una promoción pausada no avisa
        when(promotions.promotionRefs()).thenReturn(List.of(ref("PAUSED", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))));
        assertThat(kinds()).doesNotContain(Kind.MATERIAL_MISSING);
    }
}
