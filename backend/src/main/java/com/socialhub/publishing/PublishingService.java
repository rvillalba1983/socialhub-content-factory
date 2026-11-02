package com.socialhub.publishing;

import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacciona a lo que pasa en producción, dentro de la misma transacción. */
@Slf4j
@PrimaryPort
@Service
@RequiredArgsConstructor
public class PublishingService {

    static final String MANUAL_FLAG = "Publicación manual con LEEME";

    private final PublicationRepository publications;
    private final ApplicationEventPublisher events;

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
            log.info("Revisión {} aprobada: {} publicaciones de {} pasan a aprobadas", approved.taskId(), count,
                    approved.promotionId());
            events.publishEvent(new FactoryChanged("publication", null));
        }
    }

    /** Una programación fallida pasa a manual: las publicaciones con incidencia lo indican. */
    @EventListener
    @Transactional
    public void on(SwitchedToManual manual) {
        if (manual.promotionId() == null) return;
        for (Publication p : publications.findFlagged(manual.promotionId())) {
            p.flag(MANUAL_FLAG);
            publications.save(p);
        }
        events.publishEvent(new FactoryChanged("publication", null));
    }
}
