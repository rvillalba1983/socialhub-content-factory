package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublicationTest {

    private static Publication at(PublicationStatus status) {
        return new Publication("p", "promo", status, null, null, null, null, 0);
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
        assertThat(p.status()).isEqualTo(PublicationStatus.PUBLISHING);
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
        assertThat(p.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(p.confirmation()).contains("17890");
    }
}
