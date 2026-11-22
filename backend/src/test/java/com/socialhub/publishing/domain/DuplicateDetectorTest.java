package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

    private static Publication pub(String id, String promo, Platform platform, String title, String day, String time,
                                   String caption, PublicationStatus status) {
        return pub(id, promo, platform, "Reel", title, day, time, caption, status);
    }

    private static Publication pub(String id, String promo, Platform platform, String type, String title, String day, String time,
                                   String caption, PublicationStatus status) {
        var p = Publication.create(id, promo, new Publication.Details(platform, type, title, LocalDate.parse(day),
                LocalTime.parse(time), "Europe/Madrid", "Publer", null, caption, null),
                status == PublicationStatus.CANCELLED ? PublicationStatus.DRAFT : status, false);
        if (status == PublicationStatus.CANCELLED) p.moveTo(PublicationStatus.CANCELLED);
        return p;
    }

    private static String brand(String promo) { return promo.startsWith("l") ? "litoques" : "delito"; }

    @Test
    void sameTitleSameDayIgnoringCaseAccentsAndSpaces() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Poema  de AMOR", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "poema de amór", "2026-11-25", "19:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().publicationIds()).containsExactly("a", "b");
        assertThat(groups.getFirst().reason()).isEqualTo(DuplicateDetector.Reason.SAME_TITLE);
    }

    @Test
    void differentDayNetworkOrBrandIsNotADuplicate() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "Poema", "2026-11-26", "10:00", null, PublicationStatus.DRAFT),
                pub("c", "l1", Platform.TIKTOK, "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("d", "d1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).isEmpty();
    }

    @Test
    void cancelledOnesDoNotCount() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", null, PublicationStatus.CANCELLED)),
                DuplicateDetectorTest::brand);
        assertThat(groups).isEmpty();
    }

    @Test
    void sameExactTimeAndTextEvenWithDifferentTitles() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Uno", "2026-11-25", "10:00", "Hola  Mundo", PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "Dos", "2026-11-25", "10:00", "hola mundo", PublicationStatus.DRAFT),
                pub("c", "l1", Platform.INSTAGRAM, "Tres", "2026-11-25", "10:01", "hola mundo", PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().reason()).isEqualTo(DuplicateDetector.Reason.SAME_TIME_AND_TEXT);
        assertThat(groups.getFirst().publicationIds()).containsExactly("a", "b");
    }

    @Test
    void blankCaptionsAreNotCompared() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Uno", "2026-11-25", "10:00", "  ", PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "Dos", "2026-11-25", "10:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).isEmpty();
    }

    @Test
    void sameTitleButDifferentTypeIsNotADuplicate() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Reel", "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, "Carrusel", "Poema", "2026-11-25", "19:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).isEmpty();
    }

    @Test
    void sameTitleAndSameTypeIgnoringCaseCounts() {
        var groups = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Reel", "Poema", "2026-11-25", "10:00", null, PublicationStatus.DRAFT),
                pub("b", "l1", Platform.INSTAGRAM, " reel ", "Poema", "2026-11-25", "19:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(groups).hasSize(1);
    }

    @Test
    void ideasAndProposalsDoNotCountButInProductionDoes() {
        var ideas = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", "Hola", PublicationStatus.IDEA),
                pub("b", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", "Hola", PublicationStatus.DRAFT),
                pub("c", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", "Hola", PublicationStatus.PROPOSAL)),
                DuplicateDetectorTest::brand);
        assertThat(ideas).isEmpty();

        var inProduction = DuplicateDetector.find(List.of(
                pub("a", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "10:00", null, PublicationStatus.IN_PRODUCTION),
                pub("b", "l1", Platform.INSTAGRAM, "Poema", "2026-11-25", "19:00", null, PublicationStatus.DRAFT)),
                DuplicateDetectorTest::brand);
        assertThat(inProduction).hasSize(1);
    }
}
