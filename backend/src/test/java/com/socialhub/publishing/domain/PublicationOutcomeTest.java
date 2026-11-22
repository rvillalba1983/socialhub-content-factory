package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.publishing.domain.PublicationOutcome.Result;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicationOutcomeTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static Instant at(String local) { return LocalDateTime.parse(local).atZone(MADRID).toInstant(); }

    private static Publication pub(PublicationStatus status, String flag) {
        // Programada el 25 nov a las 19:00
        boolean published = status == PublicationStatus.PUBLISHED;
        return Publication.builder().id("p").promotionId("promo").platform(Platform.INSTAGRAM).type("Reel").title("Poema")
                .scheduledOn(LocalDate.parse("2026-11-25")).scheduledTime(LocalTime.of(19, 0)).timezone("Europe/Madrid")
                .status(status).route("Publer").flag(flag).confirmation(published ? "https://ig/p/1" : null)
                .publishedAt(published ? at("2026-11-25T19:02:00") : null).build();
    }

    private static PublishJob job(PublishJob.Status status, String route, String lastError, String url) {
        return new PublishJob("p", status, 0, route, null, List.of(), null, url, null, 1, lastError, null, null, null, List.of(),
                at("2026-11-20T10:00:00"), at("2026-11-20T10:00:00"), 0);
    }

    private static PublicationOutcome of(Publication p, PublishJob job, List<ExternalCalendarRepository.Item> ext, String now) {
        return PublicationOutcome.of(p, job, ext, at(now)).orElseThrow();
    }

    @Test
    void cancelledHasNoOutcome() {
        var cancelled = pub(PublicationStatus.DRAFT, null);
        cancelled.moveTo(PublicationStatus.CANCELLED);
        assertThat(PublicationOutcome.of(cancelled, null, List.of(), at("2026-11-25T20:00:00"))).isEmpty();
    }

    @Test
    void publishedShowsRealTimeRouteAndLink() {
        var o = of(pub(PublicationStatus.PUBLISHED, null), job(PublishJob.Status.PUBLISHED, "API oficial", null, "https://ig/p/1"),
                List.of(), "2026-11-25T20:00:00");
        assertThat(o.result()).isEqualTo(Result.PUBLISHED);
        assertThat(o.at()).isEqualTo(at("2026-11-25T19:02:00"));
        assertThat(o.atSource()).isEqualTo(PublicationOutcome.AtSource.CONFIRMATION);
        assertThat(o.route()).isEqualTo("API oficial");
        assertThat(o.url()).isEqualTo("https://ig/p/1");
    }

    @Test
    void calendarTimeWinsAndLateCalendarTimeIsDelayed() {
        var item = new ExternalCalendarRepository.Item(ExternalCalendarRepository.Source.PUBLER, Platform.INSTAGRAM,
                at("2026-11-25T19:40:00"), ExternalCalendarRepository.Status.PUBLISHED, null, "https://x", null, "p");
        var o = of(pub(PublicationStatus.PUBLISHED, null), job(PublishJob.Status.PUBLISHED, "Publer", null, null), List.of(item),
                "2026-11-25T21:00:00");
        assertThat(o.result()).isEqualTo(Result.DELAYED);
        assertThat(o.at()).isEqualTo(at("2026-11-25T19:40:00"));
        assertThat(o.atSource()).isEqualTo(PublicationOutcome.AtSource.CALENDAR);
        assertThat(o.url()).isEqualTo("https://x");
        assertThat(o.reason()).contains("40 min");
    }

    @Test
    void publishedWithDelayFlagIsDelayed() {
        var o = of(pub(PublicationStatus.PUBLISHED, PublicationOutcome.DELAY_PREFIX + "2 h de retraso: SocialHub no estaba abierto"),
                job(PublishJob.Status.PUBLISHED, "API oficial", null, "https://ig"), List.of(), "2026-11-25T22:00:00");
        assertThat(o.result()).isEqualTo(Result.DELAYED);
        assertThat(o.needsAttention()).isTrue();
    }

    @Test
    void errorIsFailedWithReasonAndAdvice() {
        var o = of(pub(PublicationStatus.PUBLISH_ERROR, null), job(PublishJob.Status.FAILED, "Publer", "Publer rechazó el vídeo", null),
                List.of(), "2026-11-25T20:00:00");
        assertThat(o.result()).isEqualTo(Result.FAILED);
        assertThat(o.reason()).isEqualTo("Publer rechazó el vídeo");
        assertThat(o.advice()).isNotBlank();
        // Con duda, el consejo es mirar en la red
        var doubt = of(pub(PublicationStatus.PUBLISH_ERROR, PublicationOutcome.DOUBT_PREFIX + "se cortó"),
                job(PublishJob.Status.FAILED, "API oficial", null, null), List.of(), "2026-11-25T20:00:00");
        assertThat(doubt.advice()).contains("Mira en la red");
    }

    @Test
    void beforeItsTimeOrWithinTheGraceItIsPending() {
        var scheduled = pub(PublicationStatus.SCHEDULED, null);
        var j = job(PublishJob.Status.SCHEDULED, "Publer", null, null);
        assertThat(of(scheduled, j, List.of(), "2026-11-25T18:00:00").result()).isEqualTo(Result.PENDING);
        assertThat(of(scheduled, j, List.of(), "2026-11-25T19:10:00").result()).isEqualTo(Result.PENDING);
    }

    @Test
    void pastItsTimeWithAnOpenJobIsDelayedAndWhileCheckingItIsUnconfirmed() {
        var scheduled = pub(PublicationStatus.PUBLISHING, null);
        var delayed = of(scheduled, job(PublishJob.Status.SCHEDULED, "Publer", null, null), List.of(), "2026-11-25T20:00:00");
        assertThat(delayed.result()).isEqualTo(Result.DELAYED);
        var checking = of(scheduled, job(PublishJob.Status.CHECKING, "Publer", null, null), List.of(), "2026-11-25T20:00:00");
        assertThat(checking.result()).isEqualTo(Result.UNCONFIRMED);
        assertThat(checking.route()).isEqualTo("Publer");
    }

    @Test
    void pastItsTimeWithoutAnyJobIsUnconfirmed() {
        var o = of(pub(PublicationStatus.APPROVED, null), null, List.of(), "2026-11-25T20:00:00");
        assertThat(o.result()).isEqualTo(Result.UNCONFIRMED);
        assertThat(o.at()).isNull();
    }

    @Test
    void manualRouteIsShownAsManual() {
        var o = of(pub(PublicationStatus.SCHEDULED, null), job(PublishJob.Status.MANUAL, "A mano", null, null), List.of(),
                "2026-11-25T18:00:00");
        assertThat(o.route()).isEqualTo("Manual");
    }
}
