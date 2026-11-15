package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.production.domain.events.ProductionEvents.ReviewApproved;
import com.socialhub.production.domain.events.ProductionEvents.SwitchedToManual;
import com.socialhub.promotion.PromotionService;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;
import org.springframework.transaction.support.TransactionTemplate;

/** Módulo de publicaciones con sus dependencias: reacciona a los eventos de producción sin que esta intervenga. */
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
class PublishingModuleTest {

    @Autowired PromotionService promotionService;
    @Autowired PublishingService publishing;
    @Autowired PublishingQueries queries;
    @Autowired PublicationRepository repository;
    @Autowired TransactionTemplate transactions;
    @Autowired QualityService quality;

    private String promotion() {
        var brand = promotionService.createBrand(new BrandDraft("Marca de prueba", "@prueba", "#336699"));
        return promotionService.createPromotion(new PromotionDraft(brand.id(), "Promoción de prueba", null, null, null,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), "ACTIVE", null, null, null, null, null, null,
                null, null, null)).id();
    }

    private Publication publication(String promotionId, PublicationStatus status) {
        return publishing.create(promotionId, new Publication.Details(Platform.INSTAGRAM, "Reel", "Poema",
                LocalDate.now().plusDays(5), LocalTime.NOON, "Europe/Madrid", "Publer", null, "Texto", "#poesia"), status);
    }

    private String inReview(String promotionId) {
        String id = publication(promotionId, PublicationStatus.DRAFT).getId();
        transactions.executeWithoutResult(t -> {
            Publication p = repository.findById(id).orElseThrow();
            p.moveTo(PublicationStatus.IN_REVIEW);
            repository.save(p);
        });
        return id;
    }

    @Test
    void anApprovedReviewApprovesThePublicationsInReview(Scenario scenario) {
        String promotionId = promotion();
        String id = inReview(promotionId);
        // Sin archivos, el control de calidad avisa: Raúl lo acepta a mano para que no bloquee la aprobación
        quality.accept(id, "FILES_MISSING", "Prueba del módulo");

        scenario.publish(new ReviewApproved("t-1", promotionId))
                .andWaitForStateChange(() -> queries.find(id).orElseThrow().getStatus(),
                        status -> status == PublicationStatus.APPROVED)
                .andVerify(status -> assertThat(status).isEqualTo(PublicationStatus.APPROVED));
    }

    @Test
    void anApprovedReviewDoesNotApproveAPublicationWithOpenQualityIssues(Scenario scenario) {
        String promotionId = promotion();
        String id = inReview(promotionId);

        scenario.publish(new ReviewApproved("t-3", promotionId))
                .andWaitForStateChange(() -> queries.find(id).orElseThrow().getFlag(),
                        flag -> PublishingService.QUALITY_FLAG.equals(flag))
                .andVerify(flag -> assertThat(queries.find(id).orElseThrow().getStatus())
                        .isEqualTo(PublicationStatus.IN_REVIEW));
    }

    @Test
    void switchingToManualMarksTheFlaggedPublicationsWithTheReadme(Scenario scenario) {
        String promotionId = promotion();
        Publication p = publication(promotionId, PublicationStatus.DRAFT);
        transactions.executeWithoutResult(t -> {
            Publication stored = repository.findById(p.getId()).orElseThrow();
            stored.flag("Error al programar");
            repository.save(stored);
        });

        scenario.publish(new SwitchedToManual("t-2", promotionId))
                .andWaitForStateChange(() -> queries.find(p.getId()).orElseThrow().getFlag(),
                        flag -> PublishingService.MANUAL_FLAG.equals(flag))
                .andVerify(flag -> assertThat(flag).isEqualTo(PublishingService.MANUAL_FLAG));
    }
}
