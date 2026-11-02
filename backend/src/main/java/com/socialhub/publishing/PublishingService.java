package com.socialhub.publishing;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta, edición y cambios de estado de publicaciones; y reacciones a lo que pasa en producción. */
@Slf4j
@PrimaryPort
@Service
@RequiredArgsConstructor
public class PublishingService {

    static final String MANUAL_FLAG = "Publicación manual con LEEME";

    private final PublicationRepository publications;
    private final ApplicationEventPublisher events;
    private final PromotionQueries promotions;
    private final AppModeHolder mode;
    private final Clock clock;

    /** Alta desde el centro de control. El identificador es la fecha y la plataforma: «2026-11-10-instagram». */
    @Transactional
    public Publication create(String promotionId, Publication.Details details, PublicationStatus status) {
        promotions.promotion(promotionId);
        String base = details.scheduledOn() + "-" + details.platform().name().toLowerCase(Locale.ROOT);
        String id = base;
        for (int n = 2; publications.exists(id); n++) id = base + "-" + n;
        Publication publication = publications.save(Publication.create(id, promotionId, details, status, mode.isDemo()));
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    @Transactional
    public Publication update(String id, Publication.Details details) {
        Publication publication = find(id);
        publication.edit(details, clock.instant());
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    @Transactional
    public Publication changeStatus(String id, PublicationStatus status) {
        Publication publication = find(id);
        publication.changeStatus(status, clock.instant());
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    private Publication find(String id) {
        return publications.findById(id).orElseThrow(() -> new NotFoundException("No existe la publicación " + id));
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
