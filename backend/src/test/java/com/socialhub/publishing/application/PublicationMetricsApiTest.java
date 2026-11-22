package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.MetricsRepository;
import com.socialhub.publishing.domain.MetricsRepository.Metrics;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Métricas de Instagram por la API oficial de Meta: si la publicación salió por la API y hay clave, SocialHub las lee
 * directamente y no encarga nada a Claude; si la API falla, cae a Chrome con el motivo a la vista; TikTok y lo que salió por
 * otra vía siguen siempre por Chrome. Lo que Meta no devuelve queda en null.
 */
class PublicationMetricsApiTest {

    static final Instant PUBLISHED = Instant.parse("2026-11-01T10:00:00Z");
    static final Instant NOW = PUBLISHED.plusSeconds(8 * 86_400L);

    PublicationRepository publications = mock(PublicationRepository.class);
    MetricsRepository metrics = mock(MetricsRepository.class);
    PublishJobRepository jobs = mock(PublishJobRepository.class);
    AiOrders orders = mock(AiOrders.class);
    InstagramGateway instagram = mock(InstagramGateway.class);
    InstagramCredentials credentials = mock(InstagramCredentials.class);
    List<Metrics> saved = new ArrayList<>();
    List<Publication> all = new ArrayList<>();
    PublicationMetrics service;

    @BeforeEach
    void setUp() {
        when(metrics.all()).thenReturn(List.of());
        when(publications.findAll()).thenAnswer(i -> List.copyOf(all));
        when(metrics.find(anyString())).thenAnswer(i -> saved.stream().filter(m -> m.publicationId().equals(i.getArgument(0))).reduce((a, b) -> b));
        org.mockito.Mockito.doAnswer(i -> saved.add(i.getArgument(0))).when(metrics).save(any());
        when(credentials.activeToken()).thenReturn(Optional.of("EAAB.clave-de-prueba"));
        when(orders.submitInternal(anyString(), eq(AiRequestType.METRICS_READ), anyString(), anyString(), anyList())).thenReturn("task-1");
        service = new PublicationMetrics(publications, metrics, jobs, orders,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mock(ApplicationEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC), instagram, credentials);
    }

    private void published(String id, Platform platform, String route, String mediaId) {
        var p = Publication.builder().id(id).promotionId("promo").platform(platform).type("Reel").title("Título")
                .scheduledOn(LocalDate.of(2026, 11, 1)).scheduledTime(LocalTime.NOON).status(PublicationStatus.PUBLISHED)
                .confirmation("ok").publishedAt(PUBLISHED).build();
        all.add(p);
        when(jobs.find(id)).thenReturn(Optional.of(new PublishJob(id, PublishJob.Status.PUBLISHED, 0, route, null, List.of(),
                mediaId, "https://www.instagram.com/reel/x/", null, 0, null, null, null, null, List.of(), PUBLISHED, PUBLISHED, 0)));
    }

    @Test
    void anOfficialApiPostIsReadDirectlyAndNothingIsAskedOfClaude() {
        published("p1", Platform.INSTAGRAM, "API oficial", "m-1");
        when(instagram.mediaInsights("EAAB.clave-de-prueba", "m-1"))
                .thenReturn(new InstagramGateway.MediaInsights(1200L, 800L, 64L, null, 9L, 3L));

        assertThat(service.requestDue()).as("ningún encargo").isZero();

        verify(orders, never()).submitInternal(anyString(), any(), anyString(), anyString(), anyList());
        Metrics m = saved.getLast();
        assertThat(m.status()).isEqualTo(MetricsRepository.Status.DONE);
        assertThat(m.views()).isEqualTo(1200L);
        assertThat(m.reach()).isEqualTo(800L);
        assertThat(m.comments()).as("lo que Meta no devuelve queda sin dato").isNull();
        assertThat(m.saves()).isEqualTo(9L);
        assertThat(m.note()).contains("API oficial de Meta");
    }

    @Test
    void ifTheApiFailsItFallsBackToChromeAndKeepsTheReasonVisible() {
        published("p1", Platform.INSTAGRAM, "API oficial", "m-1");
        when(instagram.mediaInsights(anyString(), anyString())).thenThrow(new InstagramException(
                InstagramException.Kind.REJECTED, "la clave no tiene permiso para ver estadísticas"));

        assertThat(service.requestDue()).isEqualTo(1);

        ArgumentCaptor<List<String>> inputs = ArgumentCaptor.forClass(List.class);
        verify(orders).submitInternal(eq("promo"), eq(AiRequestType.METRICS_READ), anyString(), anyString(), inputs.capture());
        assertThat(inputs.getValue()).containsExactly("p1");
        Metrics m = saved.getLast();
        assertThat(m.status()).isEqualTo(MetricsRepository.Status.REQUESTED);
        assertThat(m.note()).startsWith(PublicationMetrics.API_FAILED).contains("la clave no tiene permiso").contains("Chrome");
        assertThat(m.note()).doesNotContain("EAAB");
    }

    @Test
    void withoutAKeyItFallsBackToChromeWithThatReason() {
        published("p1", Platform.INSTAGRAM, "API oficial", "m-1");
        when(credentials.activeToken()).thenReturn(Optional.empty());

        assertThat(service.requestDue()).isEqualTo(1);

        verify(instagram, never()).mediaInsights(anyString(), anyString());
        assertThat(saved.getLast().note()).contains("no hay clave");
    }

    @Test
    void tikTokAndPostsThatLeftByAnotherRouteGoStraightToChrome() {
        published("tt", Platform.TIKTOK, "API oficial", "m-9");
        published("pu", Platform.INSTAGRAM, "Publer", "publer-1");

        assertThat(service.requestDue()).isEqualTo(1);

        verify(instagram, never()).mediaInsights(anyString(), anyString());
        assertThat(saved).extracting(Metrics::publicationId).containsExactlyInAnyOrder("tt", "pu");
        assertThat(saved).allSatisfy(m -> {
            assertThat(m.status()).isEqualTo(MetricsRepository.Status.REQUESTED);
            assertThat(m.note()).isNull();
        });
    }
}
