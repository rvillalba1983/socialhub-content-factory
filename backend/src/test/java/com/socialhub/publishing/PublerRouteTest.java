package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionQueries.BrandRef;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cuenta de Publer por marca y red: sin ella no se crea el encargo (queda retenida), el tope de publicaciones
 * programadas del plan gratuito se cuenta por cuenta y red sumando marcas, y los encargos dicen en qué cuenta programar.
 */
class PublerRouteTest {

    static final String TIKTOK_ACCOUNT = "Lito · Litoques · El DeLito";
    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Las publicaciones son de aquí a 20 días: hay tiempo de sobra para Publer (8 h). */
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(20);
    static final Instant NOW = Instant.now();

    private final AiOrders orders = mock(AiOrders.class);
    private final PublerAccountRepository accounts = mock(PublerAccountRepository.class);
    private final PublishJobRepository jobs = mock(PublishJobRepository.class);
    private final PublicationRepository publications = mock(PublicationRepository.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final PublishingQueries queries = mock(PublishingQueries.class);
    private final PublicationFileRepository files = mock(PublicationFileRepository.class);
    private final PublishingEngine engine = mock(PublishingEngine.class);

    private final List<PublishJob> open = new ArrayList<>();

    @BeforeEach
    void twoBrandsSharingTheTikTokAccount() {
        when(promotions.brandRefs()).thenReturn(List.of(new BrandRef("el-delito", "El DeLito"), new BrandRef("litoques", "Litoques")));
        when(promotions.brandOf("promo-delito")).thenReturn("el-delito");
        when(promotions.brandOf("promo-litoques")).thenReturn("litoques");
        when(accounts.account("el-delito", Platform.TIKTOK)).thenReturn(Optional.of(TIKTOK_ACCOUNT));
        when(accounts.account("litoques", Platform.TIKTOK)).thenReturn(Optional.of(TIKTOK_ACCOUNT));
        when(accounts.brandsUsing(TIKTOK_ACCOUNT, Platform.TIKTOK)).thenReturn(List.of("el-delito", "litoques"));
        when(accounts.brandsUsing("Litoques", Platform.INSTAGRAM)).thenReturn(List.of("litoques"));
        when(jobs.withStatus(any())).thenAnswer(i -> open);
        when(orders.submitInternal(anyString(), eq(AiRequestType.PUBLER_SCHEDULE), anyString(), anyString(), anyList())).thenReturn("task-1");
    }

    private PublerRoute route() {
        var capacity = new PublerCapacity(jobs, publications, promotions, accounts, 10);
        return new PublerRoute(orders, accounts, capacity, promotions, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private Publication publication(String id, String promotion, Platform platform, int dayOffset) {
        var p = Publication.builder().id(id).promotionId(promotion).platform(platform).type("Reel").title("Título " + id)
                .scheduledOn(DAY.plusDays(dayOffset)).scheduledTime(LocalTime.of(19, 0)).status(PublicationStatus.SCHEDULED)
                .caption("Texto").hashtags("#poesia").build();
        when(publications.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    /** Una publicación ya programada en Publer (o con el encargo en curso). */
    private void inPubler(Publication p, PublishJob.Status status, String task) {
        open.add(new PublishJob(p.getId(), status, 0, "Publer", task, List.of(), null, null, null, 1, null, null, List.of(), NOW, NOW, 0));
    }

    private PublishJob job(Publication p) {
        return PublishJob.start(p.getId(), NOW, 0);
    }

    @Test
    void withoutThePublerAccountOfTheBrandNoOrderIsCreatedAndItStaysHeldWithTheReason() {
        when(accounts.account("litoques", Platform.TIKTOK)).thenReturn(Optional.empty());
        var p = publication("p1", "promo-litoques", Platform.TIKTOK, 0);

        var outcome = route().schedule(p, job(p), NOW);

        assertThat(outcome).isEqualTo(new PublishRoute.Outcome.Held("Falta la cuenta de Publer de Litoques para TikTok"));
        verify(orders, never()).submitInternal(any(), any(), any(), any(), any());
    }

    @Test
    void theAccountOfOneNetworkDoesNotCoverTheOther() {
        when(accounts.account("litoques", Platform.INSTAGRAM)).thenReturn(Optional.empty());
        var p = publication("p1", "promo-litoques", Platform.INSTAGRAM, 0);
        assertThat(route().schedule(p, job(p), NOW))
                .isEqualTo(new PublishRoute.Outcome.Held("Falta la cuenta de Publer de Litoques para Instagram"));
    }

    @Test
    void withTheAccountTheOrderIsCreated() {
        var p = publication("p1", "promo-litoques", Platform.TIKTOK, 0);
        assertThat(route().schedule(p, job(p), NOW)).isEqualTo(new PublishRoute.Outcome.Pending("task-1"));
        verify(orders).submitInternal(eq("promo-litoques"), eq(AiRequestType.PUBLER_SCHEDULE), anyString(), anyString(), eq(List.of("p1")));
    }

    @Test
    void theTikTokLimitIsCountedAcrossTheTwoBrandsThatShareTheAccount() {
        // 5 de El DeLito y 4 de Litoques: 9 en la cuenta compartida, todavía cabe una
        IntStream.range(0, 5).forEach(n -> inPubler(publication("d" + n, "promo-delito", Platform.TIKTOK, n), PublishJob.Status.SCHEDULED, null));
        IntStream.range(0, 4).forEach(n -> inPubler(publication("l" + n, "promo-litoques", Platform.TIKTOK, n), PublishJob.Status.SCHEDULED, null));
        var tenth = publication("l9", "promo-litoques", Platform.TIKTOK, 9);
        assertThat(route().schedule(tenth, job(tenth), NOW)).isEqualTo(new PublishRoute.Outcome.Pending("task-1"));

        // Con la décima ya programada (de El DeLito), la undécima, de cualquiera de las dos marcas, queda en espera
        inPubler(publication("d9", "promo-delito", Platform.TIKTOK, 9), PublishJob.Status.SCHEDULED, null);
        var eleventhLitoques = publication("l10", "promo-litoques", Platform.TIKTOK, 10);
        var held = route().schedule(eleventhLitoques, job(eleventhLitoques), NOW);
        assertThat(held).isInstanceOfSatisfying(PublishRoute.Outcome.Held.class, h -> assertThat(h.reason())
                .contains("«" + TIKTOK_ACCOUNT + "»").contains("10 publicaciones programadas").contains("en espera"));
        var eleventhDelito = publication("d10", "promo-delito", Platform.TIKTOK, 10);
        assertThat(route().schedule(eleventhDelito, job(eleventhDelito), NOW)).isInstanceOf(PublishRoute.Outcome.Held.class);
    }

    @Test
    void onlyThoseStillWaitingToBePublishedInThatAccountAndNetworkCount() {
        // Ocho que cuentan…
        IntStream.range(0, 8).forEach(n -> inPubler(publication("d" + n, "promo-delito", Platform.TIKTOK, n), PublishJob.Status.SCHEDULED, null));
        // …y otras que no: ya salieron (su hora pasó), son de otra red, de otra vía, están sin encargo en curso o son la propia
        var past = Publication.builder().id("past").promotionId("promo-delito").platform(Platform.TIKTOK).type("Reel").title("Antigua")
                .scheduledOn(LocalDate.now(MADRID).minusDays(1)).scheduledTime(LocalTime.of(10, 0)).status(PublicationStatus.PUBLISHING).build();
        when(publications.findById("past")).thenReturn(Optional.of(past));
        inPubler(past, PublishJob.Status.SCHEDULED, null);
        inPubler(publication("ig", "promo-litoques", Platform.INSTAGRAM, 1), PublishJob.Status.SCHEDULED, null);
        var other = publication("publora", "promo-delito", Platform.TIKTOK, 11);
        open.add(new PublishJob(other.getId(), PublishJob.Status.SCHEDULED, 1, "Publora", null, List.of(), null, null, null, 1, null, null,
                List.of(), NOW, NOW, 0));
        inPubler(publication("sin-encargo", "promo-delito", Platform.TIKTOK, 12), PublishJob.Status.WORKING, null);
        var mine = publication("mine", "promo-litoques", Platform.TIKTOK, 13);
        inPubler(mine, PublishJob.Status.SCHEDULED, null);

        // 8 + la propia (que no se cuenta) = 8: cabe. Con un encargo en curso más (9) y otro (10) ya no
        assertThat(route().schedule(mine, job(mine), NOW)).isEqualTo(new PublishRoute.Outcome.Pending("task-1"));
        inPubler(publication("en-curso-1", "promo-delito", Platform.TIKTOK, 14), PublishJob.Status.WORKING, "t-a");
        assertThat(route().schedule(mine, job(mine), NOW)).isEqualTo(new PublishRoute.Outcome.Pending("task-1"));
        inPubler(publication("en-curso-2", "promo-litoques", Platform.TIKTOK, 15), PublishJob.Status.WORKING, "t-b");
        assertThat(route().schedule(mine, job(mine), NOW)).isInstanceOf(PublishRoute.Outcome.Held.class);
    }

    @Test
    void theLimitIsTenByDefaultAndConfigurable() throws Exception {
        var parameter = PublerCapacity.class.getDeclaredConstructors()[0].getParameters()[4];
        assertThat(parameter.getAnnotation(Value.class).value()).isEqualTo("${socialhub.publer.scheduled-limit:10}");
    }

    // ------------------------------------------------------------------ Lo que lleva el encargo

    private PublerScheduleHandler handler() {
        return new PublerScheduleHandler(queries, files, engine, accounts);
    }

    private AiJob aiJob(Publication p, String brand) {
        return new AiJob("t-1", AiRequestType.PUBLER_SCHEDULE, p.getPromotionId(), brand, "Programar", "x", List.of(p.getId()), NOW);
    }

    @Test
    void theOrderSaysWhichPublerAccountAndTheTikTokOptions() {
        var p = publication("p1", "promo-litoques", Platform.TIKTOK, 0);
        when(queries.find("p1")).thenReturn(Optional.of(p));
        when(files.files("p1")).thenReturn(List.of());

        var inputs = handler().inputs(aiJob(p, "litoques"));

        assertThat(inputs.get("publer_account")).isEqualTo(TIKTOK_ACCOUNT);
        @SuppressWarnings("unchecked") var how = (List<String>) inputs.get("how");
        String text = String.join("\n", how);
        assertThat(text).contains("en la cuenta «" + TIKTOK_ACCOUNT + "» de Publer")
                .contains("«Todo el mundo»").contains("Comentarios, Dúo y Stitch").contains("los sonidos ya van en el vídeo");
    }

    @Test
    void theTwoBrandsOfTheSharedTikTokAccountGetTheSameAccountInTheirOrders() {
        var a = publication("a", "promo-delito", Platform.TIKTOK, 0);
        var b = publication("b", "promo-litoques", Platform.TIKTOK, 1);
        when(queries.find("a")).thenReturn(Optional.of(a));
        when(queries.find("b")).thenReturn(Optional.of(b));
        when(files.files(anyString())).thenReturn(List.of());
        assertThat(handler().inputs(aiJob(a, "el-delito")).get("publer_account")).isEqualTo(TIKTOK_ACCOUNT);
        assertThat(handler().inputs(aiJob(b, "litoques")).get("publer_account")).isEqualTo(TIKTOK_ACCOUNT);
    }

    @Test
    void instagramOrdersNameTheAccountButDoNotCarryTheTikTokOptions() {
        when(accounts.account("litoques", Platform.INSTAGRAM)).thenReturn(Optional.of("Litoques"));
        var p = publication("p1", "promo-litoques", Platform.INSTAGRAM, 0);
        when(queries.find("p1")).thenReturn(Optional.of(p));
        when(files.files("p1")).thenReturn(List.of());

        var inputs = handler().inputs(aiJob(p, "litoques"));

        assertThat(inputs.get("publer_account")).isEqualTo("Litoques");
        @SuppressWarnings("unchecked") var how = (List<String>) inputs.get("how");
        assertThat(String.join("\n", how)).contains("en la cuenta «Litoques» de Publer").doesNotContain("Todo el mundo").doesNotContain("Stitch");
    }

    @Test
    void ifTheAccountDisappearsBeforeTheOrderGoesOutClaudeIsToldNotToScheduleAnything() {
        when(accounts.account("litoques", Platform.TIKTOK)).thenReturn(Optional.empty());
        var p = publication("p1", "promo-litoques", Platform.TIKTOK, 0);
        when(queries.find("p1")).thenReturn(Optional.of(p));
        when(files.files("p1")).thenReturn(List.of());

        var inputs = handler().inputs(aiJob(p, "litoques"));

        assertThat(inputs).containsKey("publer_account");
        assertThat(inputs.get("publer_account")).isNull();
        @SuppressWarnings("unchecked") var how = (List<String>) inputs.get("how");
        assertThat(how).hasSize(1);
        assertThat(how.getFirst()).contains("no programes nada").contains("failed");
    }

    @Test
    void theCheckOrderNamesTheAccountToo() {
        var p = publication("p1", "promo-litoques", Platform.TIKTOK, 0);
        when(queries.find("p1")).thenReturn(Optional.of(p));
        when(files.files("p1")).thenReturn(List.of());
        when(engine.job("p1")).thenReturn(Optional.empty());
        var check = new PublishCheckHandler(queries, files, engine, accounts);

        var inputs = check.inputs(new AiJob("t-2", AiRequestType.PUBLISH_CHECK, "promo-litoques", "litoques", "Comprobar", "x", List.of("p1"), NOW));

        assertThat(inputs.get("publer_account")).isEqualTo(TIKTOK_ACCOUNT);
        @SuppressWarnings("unchecked") var how = (List<String>) inputs.get("how");
        assertThat(how.getFirst()).contains("en la cuenta «" + TIKTOK_ACCOUNT + "» de Publer");
    }

    @Test
    void theCalendarReadOrderListsThePublerAccountsOfTheBrand() {
        when(promotions.handleOf("litoques")).thenReturn("@litoques");
        when(promotions.promotionIdsOf("litoques")).thenReturn(List.of());
        when(promotions.brandOf("promo-litoques")).thenReturn("litoques");
        when(accounts.accounts("litoques")).thenReturn(Map.of(Platform.INSTAGRAM, "Litoques", Platform.TIKTOK, TIKTOK_ACCOUNT));
        var read = new CalendarReadHandler(queries, promotions, mock(ExternalCalendar.class), accounts);

        var inputs = read.inputs(new AiJob("t-3", AiRequestType.CALENDAR_READ, "promo-litoques", "litoques", "Leer", "x",
                List.of(DAY.toString()), NOW));

        assertThat(inputs.get("publer_accounts")).isEqualTo(Map.of("INSTAGRAM", "Litoques", "TIKTOK", TIKTOK_ACCOUNT));
        @SuppressWarnings("unchecked") var how = (List<String>) inputs.get("how");
        assertThat(how.getFirst()).contains("publer_accounts");
        assertThat(JsonMapper.builder().build().writeValueAsString(inputs)).contains("Lito · Litoques · El DeLito");
    }
}
