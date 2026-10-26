package com.socialhub.publishing;

import com.socialhub.audit.AuditLog;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacciona a lo que pasa en producción, dentro de la misma transacción. */
@Service
public class PublishingService {

    private final PublicationRepository publications;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;

    public PublishingService(PublicationRepository publications, ApplicationEventPublisher events, AuditLog audit) {
        this.publications = publications;
        this.events = events;
        this.audit = audit;
    }

    /** Raúl aprueba la revisión: las publicaciones en revisión de esa promoción pasan a «Aprobada». */
    @EventListener
    @Transactional
    public void on(ReviewApproved approved) {
        if (approved.promotionId() == null) return;
        int count = 0;
        for (Publication p : publications.findByPromotionAndStatus(approved.promotionId(), PublicationStatus.IN_REVIEW)) {
            p.moveTo(PublicationStatus.APPROVED);
            publications.save(p);
            count++;
        }
        if (count > 0) {
            audit.record("Raúl", "publication.approve", "promotion", approved.promotionId(), count + " publicaciones");
            events.publishEvent(new FactoryChanged("publication", null));
        }
    }

    /** Una programación fallida pasa a manual: las publicaciones con incidencia lo indican. */
    @EventListener
    @Transactional
    public void on(SwitchedToManual manual) {
        if (manual.promotionId() == null) return;
        for (Publication p : publications.findFlagged(manual.promotionId())) {
            p.flag("Publicación manual con LEEME");
            publications.save(p);
        }
        events.publishEvent(new FactoryChanged("publication", null));
    }
}
