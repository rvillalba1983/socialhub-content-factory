package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.publishing.domain.MetricsRepository.Metrics;
import com.socialhub.publishing.domain.MetricsRepository.Status;
import com.socialhub.publishing.domain.PromotionMetricsReport.FormatAverages;
import com.socialhub.publishing.domain.PromotionMetricsReport.NetworkTotals;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PromotionMetricsReportTest {

    private static Publication pub(String id, Platform platform, String type, PublicationStatus status) {
        return Publication.builder().id(id).promotionId("promo").platform(platform).type(type).title(id)
                .scheduledOn(LocalDate.of(2026, 11, 2)).scheduledTime(LocalTime.of(19, 0)).status(status)
                .confirmation(status == PublicationStatus.PUBLISHED ? "ok" : null).build();
    }

    private static Metrics done(String id, @Nullable Long views, @Nullable Long reach, @Nullable Long likes) {
        return new Metrics(id, Status.DONE, null, 1, Instant.EPOCH, Instant.EPOCH, views, reach, likes, null, null, null, null);
    }

    private static Metrics other(String id, Status status) {
        return new Metrics(id, status, null, 1, Instant.EPOCH, null, null, null, null, null, null, null, null);
    }

    private static NetworkTotals network(PromotionMetricsReport r, @Nullable Platform p) {
        return r.byNetwork().stream().filter(n -> n.platform() == p).findFirst().orElseThrow();
    }

    @Test
    void withoutMetricsEverythingIsEmptyAndPublishedOnesArePending() {
        var r = PromotionMetricsReport.of(List.of(pub("a", Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED),
                pub("b", Platform.TIKTOK, "Vídeo", PublicationStatus.PUBLISHED),
                pub("c", Platform.TIKTOK, "Vídeo", PublicationStatus.DRAFT)), Map.of());
        assertThat(r.measured()).isZero();
        assertThat(r.pending()).isEqualTo(2);
        assertThat(r.byFormat()).isEmpty();
        assertThat(r.byNetwork()).hasSize(3);
        assertThat(r.byNetwork()).allSatisfy(n -> {
            assertThat(n.publications()).isZero();
            assertThat(n.sums().views()).isNull();
        });
    }

    @Test
    void requestedAndFailedMetricsDoNotCountAndStayPending() {
        var pubs = List.of(pub("a", Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED),
                pub("b", Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED),
                pub("c", Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED));
        var r = PromotionMetricsReport.of(pubs, Map.of("a", other("a", Status.REQUESTED), "b", other("b", Status.FAILED),
                "c", done("c", 10L, 5L, 2L)));
        assertThat(r.measured()).isEqualTo(1);
        assertThat(r.pending()).isEqualTo(2);
        assertThat(network(r, Platform.INSTAGRAM).publications()).isEqualTo(1);
    }

    @Test
    void sumsPerNetworkAndTotalSkippingNullFigures() {
        var pubs = List.of(pub("a", Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED),
                pub("b", Platform.INSTAGRAM, "Carrusel", PublicationStatus.PUBLISHED),
                pub("c", Platform.TIKTOK, "Vídeo", PublicationStatus.PUBLISHED));
        Map<String, Metrics> m = new HashMap<>();
        m.put("a", done("a", 100L, 50L, null));
        m.put("b", done("b", null, 70L, null));
        m.put("c", done("c", 300L, null, 8L));
        var r = PromotionMetricsReport.of(pubs, m);

        assertThat(r.measured()).isEqualTo(3);
        assertThat(r.pending()).isZero();
        var ig = network(r, Platform.INSTAGRAM);
        assertThat(ig.publications()).isEqualTo(2);
        assertThat(ig.sums().views()).isEqualTo(100L);
        assertThat(ig.sums().reach()).isEqualTo(120L);
        assertThat(ig.sums().likes()).isNull(); // ninguna publicación tiene la cifra: sin dato, no cero
        var tt = network(r, Platform.TIKTOK);
        assertThat(tt.sums().views()).isEqualTo(300L);
        assertThat(tt.sums().reach()).isNull();
        var total = network(r, null);
        assertThat(total.publications()).isEqualTo(3);
        assertThat(total.sums().views()).isEqualTo(400L);
        assertThat(total.sums().reach()).isEqualTo(120L);
        assertThat(total.sums().likes()).isEqualTo(8L);
        assertThat(total.sums().comments()).isNull();
        assertThat(r.byNetwork().get(2).platform()).isNull(); // TOTAL va la última
    }

    @Test
    void comparesFormatsWithMeansAndFlagsSmallGroups() {
        List<Publication> pubs = new java.util.ArrayList<>();
        Map<String, Metrics> m = new HashMap<>();
        for (int i = 1; i <= 5; i++) { // 5 reels de Instagram: justo el límite, no es poco dato
            pubs.add(pub("r" + i, Platform.INSTAGRAM, "Reel", PublicationStatus.PUBLISHED));
            m.put("r" + i, done("r" + i, i * 100L, null, 10L));
        }
        for (int i = 1; i <= 2; i++) { // 2 carruseles de Instagram: pocos datos
            pubs.add(pub("c" + i, Platform.INSTAGRAM, "Carrusel", PublicationStatus.PUBLISHED));
            m.put("c" + i, done("c" + i, i == 1 ? 1000L : null, null, null));
        }
        pubs.add(pub("t1", Platform.TIKTOK, "Reel", PublicationStatus.PUBLISHED));
        m.put("t1", done("t1", 40L, 20L, null));
        var r = PromotionMetricsReport.of(pubs, m);

        assertThat(r.byFormat()).extracting(f -> f.type() + "/" + f.platform())
                .containsExactly("Carrusel/INSTAGRAM", "Reel/INSTAGRAM", "Reel/TIKTOK");
        FormatAverages reels = r.byFormat().get(1);
        assertThat(reels.publications()).isEqualTo(5);
        assertThat(reels.lowSample()).isFalse();
        assertThat(reels.averages().views()).isEqualTo(300.0); // media, no suma (1.500)
        assertThat(reels.averages().likes()).isEqualTo(10.0);
        assertThat(reels.averages().reach()).isNull();
        FormatAverages carousels = r.byFormat().get(0);
        assertThat(carousels.lowSample()).isTrue();
        assertThat(carousels.averages().views()).isEqualTo(1000.0); // media solo entre quien tiene la cifra
        assertThat(carousels.averages().likes()).isNull();
        assertThat(r.byFormat().get(2).lowSample()).isTrue();
    }
}
