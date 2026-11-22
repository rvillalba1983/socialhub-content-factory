package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionCommands;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.domain.DuplicateDetector;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

/** Duplicados, resultado y «Mover a la siguiente hora libre» con el módulo entero. */
@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)
@org.springframework.test.context.TestPropertySource(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.ai.initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@org.springframework.context.annotation.Import(com.socialhub.PinnedClock.class)
class PublicationInsightsTest {

    @Autowired PromotionCommands promotionService;
    @Autowired PublishingService publishing;
    @Autowired PublishingQueriesService queries;

    private final LocalDate end = LocalDate.now().plusDays(30);

    private String promotion() {
        var brand = promotionService.createBrand(new BrandDraft("Marca de prueba", "@prueba", "#336699"));
        return promotionService.createPromotion(new PromotionDraft(brand.id(), "Promoción de prueba", null, null, null, null,
                LocalDate.now().plusDays(1), end, "ACTIVE", null, null, null, null, null, null,
                null, null, null)).id();
    }

    private Publication pub(String promotion, String title, LocalDate day, String time, PublicationStatus status) {
        return publishing.create(promotion, new Publication.Details(Platform.INSTAGRAM, "Reel", title, day,
                LocalTime.parse(time), "Europe/Madrid", "Publer", null, null, null), status);
    }

    private static LocalDate soon() { return LocalDate.now().plusDays(10); }

    @Test
    void clashingPublicationGetsTheNextFreeSlotAndMovesOnlyWhenConfirmed() {
        String promo = promotion();
        pub(promo, "Primera", soon(), "10:00", PublicationStatus.DRAFT);
        var second = pub(promo, "Segunda", soon(), "10:10", PublicationStatus.DRAFT);

        var proposal = queries.nextFreeSlot(second.getId());
        assertThat(proposal.reason()).isEqualTo(PublishingQueries.MoveReason.CLASH);
        assertThat(proposal.newDay()).isEqualTo(soon());
        assertThat(proposal.newTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(proposal.changesDay()).isFalse();
        assertThat(queries.insights().get(second.getId()).move()).contains(PublishingQueries.MoveReason.CLASH);

        // Nunca mueve sin confirmar
        assertThatThrownBy(() -> publishing.moveToFreeSlot(second.getId(), soon(), LocalTime.of(10, 30), false, false))
                .isInstanceOf(IllegalArgumentException.class);
        // Una hora que ya no es la propuesta se rechaza
        assertThatThrownBy(() -> publishing.moveToFreeSlot(second.getId(), soon(), LocalTime.of(11, 0), true, false))
                .isInstanceOf(DomainException.class).hasMessageContaining("ha cambiado");

        var moved = publishing.moveToFreeSlot(second.getId(), soon(), LocalTime.of(10, 30), true, false);
        assertThat(moved.getScheduledTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(queries.insights().get(second.getId()).move()).isEmpty();
        // Ya no hace falta moverla
        assertThatThrownBy(() -> queries.nextFreeSlot(second.getId())).isInstanceOf(DomainException.class);
    }

    @Test
    void changingDayAndPassingThePromotionEndNeedsExplicitAcceptance() {
        String promo = promotion();
        pub(promo, "Tarde", end, "23:45", PublicationStatus.IN_PRODUCTION);
        var last = pub(promo, "Última", end, "23:50", PublicationStatus.IN_PRODUCTION);

        var proposal = queries.nextFreeSlot(last.getId());
        assertThat(proposal.newDay()).isEqualTo(end.plusDays(1));
        assertThat(proposal.newTime()).isEqualTo(LocalTime.of(0, 15));
        assertThat(proposal.changesDay()).isTrue();
        assertThat(proposal.afterPromotionEnd()).isTrue();
        assertThat(proposal.promotionEndsOn()).isEqualTo(end);

        assertThatThrownBy(() -> publishing.moveToFreeSlot(last.getId(), end.plusDays(1), LocalTime.of(0, 15), true, false))
                .isInstanceOf(DomainException.class).hasMessageContaining("fin de la promoción");
        var moved = publishing.moveToFreeSlot(last.getId(), end.plusDays(1), LocalTime.of(0, 15), true, true);
        assertThat(moved.getScheduledOn()).isEqualTo(end.plusDays(1));
    }

    @Test
    void anIdeaCannotBeMoved() {
        String promo = promotion();
        var p = pub(promo, "Idea", soon(), "10:00", PublicationStatus.IDEA);
        assertThat(queries.insights().get(p.getId()).move()).isEmpty();
        assertThatThrownBy(() -> queries.nextFreeSlot(p.getId())).isInstanceOf(DomainException.class);
    }

    @Test
    void duplicatesAreFlaggedUntilOneIsDiscarded() {
        String promo = promotion();
        var a = pub(promo, "Poema de amor", soon(), "10:00", PublicationStatus.DRAFT);
        var b = pub(promo, "POEMA  de amor", soon(), "19:00", PublicationStatus.DRAFT);
        var insights = queries.insights();
        assertThat(insights.get(a.getId()).duplicates()).extracting(PublishingQueries.DuplicateRef::publicationId).containsExactly(b.getId());
        assertThat(insights.get(a.getId()).duplicates().getFirst().reason()).isEqualTo(DuplicateDetector.Reason.SAME_TITLE);
        assertThat(queries.duplicateGroups()).anyMatch(g -> g.publicationIds().contains(a.getId()));

        publishing.changeStatus(b.getId(), PublicationStatus.CANCELLED, com.socialhub.publishing.domain.RejectionReason.REPEATED, null);
        assertThat(queries.insights().get(a.getId()).duplicates()).isEmpty();
        assertThat(queries.insights().get(b.getId()).outcome()).isEmpty();
        assertThat(queries.duplicateGroups()).noneMatch(g -> g.publicationIds().contains(a.getId()));
    }
}
