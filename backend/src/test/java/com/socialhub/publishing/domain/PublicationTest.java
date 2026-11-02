package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class PublicationTest {

    private static Publication at(PublicationStatus status) {
        return Publication.builder().id("p").promotionId("promo").platform(Platform.INSTAGRAM).type("Reel")
                .title("Prueba").scheduledOn(LocalDate.of(2026, 11, 2)).scheduledTime(LocalTime.of(19, 0))
                .status(status)
                .confirmation(status == PublicationStatus.PUBLISHED ? "Vista de Publer" : null)
                .build();
    }

    @Test
    void followsTheEditorialFlow() {
        Publication p = at(PublicationStatus.IDEA);
        p.moveTo(PublicationStatus.PROPOSAL);
        p.moveTo(PublicationStatus.IN_PRODUCTION);
        p.moveTo(PublicationStatus.DRAFT);
        p.moveTo(PublicationStatus.IN_REVIEW);
        p.moveTo(PublicationStatus.APPROVED);
        p.moveTo(PublicationStatus.SCHEDULED);
        p.moveTo(PublicationStatus.PUBLISHING);
        assertThat(p.getStatus()).isEqualTo(PublicationStatus.PUBLISHING);
    }

    @Test
    void cannotSkipSteps() {
        assertThatThrownBy(() -> at(PublicationStatus.IDEA).moveTo(PublicationStatus.SCHEDULED))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> at(PublicationStatus.PUBLISHED).moveTo(PublicationStatus.CANCELLED))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void neverBecomesPublishedWithoutConfirmation() {
        Publication p = at(PublicationStatus.PUBLISHING);
        assertThatThrownBy(() -> p.moveTo(PublicationStatus.PUBLISHED)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> p.confirmPublished(" ", null, Instant.now())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> at(PublicationStatus.SCHEDULED).confirmPublished("123", null, Instant.now()))
                .isInstanceOf(DomainException.class);

        p.confirmPublished("17890", null, Instant.parse("2026-10-26T18:00:00Z"));
        assertThat(p.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(p.getConfirmation()).contains("17890");
    }

    @Test
    void aPublishedPublicationCannotBeLoadedWithoutConfirmation() {
        assertThatThrownBy(() -> Publication.builder().id("p").promotionId("promo").platform(Platform.TIKTOK)
                .type("Vídeo").title("x").scheduledOn(LocalDate.of(2026, 11, 2)).scheduledTime(LocalTime.NOON)
                .status(PublicationStatus.PUBLISHED).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 2 nov 2026, 12:00 en Madrid (UTC+1): la publicación de prueba es a las 19:00, 7 horas después
    private static final Instant NOON = Instant.parse("2026-11-02T11:00:00Z");

    private static Publication.Details details(LocalTime time) {
        return new Publication.Details(Platform.TIKTOK, "Reel", "Nuevo", LocalDate.of(2026, 11, 2), time,
                "Europe/Madrid", "Publer", " ", "Texto", "#poesia");
    }

    @Test
    void schedulingNeedsSixHoursOfMargin() {
        Publication p = at(PublicationStatus.APPROVED);
        p.changeStatus(PublicationStatus.SCHEDULED, NOON);
        assertThat(p.getStatus()).isEqualTo(PublicationStatus.SCHEDULED);

        Publication late = at(PublicationStatus.APPROVED);
        assertThatThrownBy(() -> late.changeStatus(PublicationStatus.SCHEDULED, NOON.plusSeconds(3600 + 1)))
                .isInstanceOf(DomainException.class).hasMessageContaining("6 horas");
        // Ya programada: no se puede acercar la hora a menos de 6 horas
        assertThatThrownBy(() -> p.edit(details(LocalTime.of(17, 0)), NOON)).isInstanceOf(DomainException.class);
        p.edit(details(LocalTime.of(20, 0)), NOON);
        assertThat(p.getPlatform()).isEqualTo(Platform.TIKTOK);
        assertThat(p.getNote()).isNull();
    }

    @Test
    void publishingStatesAreNeverSetByHand() {
        assertThatThrownBy(() -> at(PublicationStatus.SCHEDULED).changeStatus(PublicationStatus.PUBLISHING, NOON))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> at(PublicationStatus.PUBLISHING).changeStatus(PublicationStatus.PUBLISHED, NOON))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> at(PublicationStatus.PUBLISHED).edit(details(LocalTime.NOON), NOON))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void newPublicationsStartBeforeReview() {
        assertThat(Publication.create("x", "promo", details(LocalTime.NOON), PublicationStatus.DRAFT, false).getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
        assertThatThrownBy(() -> Publication.create("x", "promo", details(LocalTime.NOON), PublicationStatus.SCHEDULED, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Publication.Details(Platform.TIKTOK, "Reel", "T", LocalDate.of(2026, 11, 2),
                LocalTime.NOON, "Marte/Olympus", "Publer", null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
