package com.socialhub.publishing;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.domain.events.ProductionEvents.ReviewApproved;
import com.socialhub.production.domain.events.ProductionEvents.SwitchedToManual;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
    static final String QUALITY_FLAG = "Control de calidad pendiente";

    private final PublicationRepository publications;
    private final ApplicationEventPublisher events;
    private final PromotionQueries promotions;
    private final AppModeHolder mode;
    private final Clock clock;
    private final PublicationFileRepository files;
    private final QualityService quality;
    private final com.socialhub.publishing.domain.EditorialMemoryRepository memory;
    private final PublishingEngine engine;

    /** Alta desde el centro de control. El identificador es la fecha y la plataforma: «2026-11-10-instagram». */
    @Transactional
    public Publication create(String promotionId, Publication.Details details, PublicationStatus status) {
        promotions.requirePromotion(promotionId);
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
        Publication.TextCorrection correction = publication.edit(details, clock.instant());
        if (correction != null) {
            memory.add(new com.socialhub.publishing.domain.EditorialMemoryRepository.Correction(
                    promotions.brandOf(publication.getPromotionId()), publication.getPromotionId(), id,
                    correction.beforeCaption(), correction.afterCaption(), correction.beforeHashtags(),
                    correction.afterHashtags(), clock.instant()));
        }
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    @Transactional
    public Publication changeStatus(String id, PublicationStatus status) {
        return changeStatus(id, status, null, null);
    }

    /** Cambio de estado. Para cancelar hace falta el motivo, que queda en la memoria editorial. */
    @Transactional
    public Publication changeStatus(String id, PublicationStatus status,
                                    com.socialhub.publishing.domain.@Nullable RejectionReason reason, @Nullable String note) {
        Publication publication = find(id);
        if (status == PublicationStatus.CANCELLED && reason == null) {
            throw new IllegalArgumentException("Para cancelar una publicación indica el motivo");
        }
        if (status == PublicationStatus.APPROVED || status == PublicationStatus.SCHEDULED) {
            long open = quality.openIssues(id);
            if (open > 0) {
                throw new com.socialhub.platform.DomainException("No pasa el control de calidad: " + open
                        + (open == 1 ? " aviso sin resolver" : " avisos sin resolver")
                        + ". Corrígelos o acéptalos uno a uno en la publicación.");
            }
        }
        PublicationStatus before = publication.getStatus();
        publication.changeStatus(status, clock.instant());
        if (!mode.isDemo()) {
            if (status == PublicationStatus.SCHEDULED) engine.start(publication);
            else if (before == PublicationStatus.SCHEDULED) engine.stop(publication, "Raúl la ha pasado a «" + status.label() + "»");
        }
        if (status == PublicationStatus.CANCELLED) {
            memory.add(new com.socialhub.publishing.domain.EditorialMemoryRepository.Rejection(
                    promotions.brandOf(publication.getPromotionId()), publication.getPromotionId(), id,
                    com.socialhub.publishing.domain.EditorialMemoryRepository.Kind.PUBLICATION, reason, blank(note),
                    publication.getTitle(), publication.getCaption(), clock.instant()));
        }
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    /** Raúl da por resuelta la incidencia anotada. */
    @Transactional
    public void clearFlag(String id) {
        Publication publication = find(id);
        if (publication.getFlag() == null) return;
        publication.flag(null);
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
    }

    /** Asocia a mano un archivo de las carpetas de la promoción. */
    @Transactional
    public PublicationFile attachManually(String publicationId, String path) {
        Publication publication = find(publicationId);
        publication.requireEditable();
        Path real = insideFolders(publication.getPromotionId(), path);
        var file = new PublicationFile(real.toString(), PublicationFile.Origin.MANUAL, clock.instant());
        if (!files.add(publicationId, file)) throw new IllegalArgumentException("Ese archivo ya está asociado");
        events.publishEvent(new FactoryChanged("publication", publicationId));
        return file;
    }

    /** Asocia los archivos generados para una pieza del spec.json. Los que ya estaban se dejan como están. */
    @Transactional
    public int attachGenerated(String promotionId, String publicationId, List<Path> generated) {
        Publication publication = find(publicationId);
        if (!publication.getPromotionId().equals(promotionId)) {
            throw new IllegalArgumentException("La publicación " + publicationId + " no es de esta promoción");
        }
        int added = 0;
        if (publication.getStatus() == PublicationStatus.SCHEDULED || publication.getStatus() == PublicationStatus.PUBLISHING
                || publication.getStatus() == PublicationStatus.PUBLISHED) {
            log.info("Piezas generadas sin asociar: {} ya está programada o publicada", publicationId);
            return 0;
        }
        for (Path path : generated) {
            Path real = insideFolders(promotionId, path.toString());
            if (files.add(publicationId, new PublicationFile(real.toString(), PublicationFile.Origin.SPEC, clock.instant()))) {
                added++;
            }
        }
        events.publishEvent(new FactoryChanged("publication", publicationId));
        return added;
    }

    /** Quita la asociación. El archivo no se toca. */
    @Transactional
    public void detach(String publicationId, String path) {
        find(publicationId).requireEditable();
        if (!files.remove(publicationId, path)) throw new NotFoundException("Ese archivo no está asociado");
        events.publishEvent(new FactoryChanged("publication", publicationId));
    }

    /** Ruta real de un archivo existente dentro de las carpetas de la promoción (ni {@code ..} ni enlaces llevan fuera). */
    private Path insideFolders(String promotionId, String path) {
        Path real;
        try {
            Path p = Path.of(path);
            if (!p.isAbsolute()) throw new IllegalArgumentException("La ruta del archivo debe ser completa: " + path);
            real = p.toRealPath();
        } catch (InvalidPathException | IOException e) {
            throw new IllegalArgumentException("No existe el archivo " + path);
        }
        if (!Files.isRegularFile(real)) throw new IllegalArgumentException("No es un archivo: " + path);
        List<Path> roots = new ArrayList<>();
        for (String folder : promotions.driveFolders(promotionId)) {
            try {
                roots.add(Path.of(folder).toRealPath());
            } catch (InvalidPathException | IOException e) {
                // Carpeta no disponible en este ordenador
            }
        }
        if (roots.stream().noneMatch(real::startsWith)) {
            throw new IllegalArgumentException("El archivo está fuera de las carpetas de la promoción: " + path);
        }
        return real;
    }

    /** Texto y hashtags de una publicación, para guardarlos juntos. */
    public record Texts(String publicationId, String caption, String hashtags) {}

    /** Guarda los textos propuestos. Comprueba todas las publicaciones antes de cambiar ninguna. */
    @Transactional
    public void applyTexts(String promotionId, List<Texts> texts) {
        List<Publication> found = new ArrayList<>();
        for (Texts t : texts) {
            Publication p = find(t.publicationId());
            if (!p.getPromotionId().equals(promotionId)) {
                throw new IllegalArgumentException("La publicación " + t.publicationId() + " no es de esta promoción");
            }
            if (p.getStatus() == PublicationStatus.PUBLISHING || p.getStatus() == PublicationStatus.PUBLISHED) {
                throw new IllegalArgumentException("La publicación " + t.publicationId() + " ya se está publicando o está publicada");
            }
            found.add(p);
        }
        for (int i = 0; i < texts.size(); i++) {
            found.get(i).proposeTexts(texts.get(i).caption(), texts.get(i).hashtags());
            publications.save(found.get(i));
            events.publishEvent(new FactoryChanged("publication", found.get(i).getId()));
        }
    }

    /** Raúl descarta el texto de la publicación con su motivo: queda en la memoria editorial. */
    @Transactional
    public Publication discardTexts(String id, com.socialhub.publishing.domain.RejectionReason reason, @Nullable String note) {
        Publication publication = find(id);
        String discarded = publication.discardTexts();
        memory.add(new com.socialhub.publishing.domain.EditorialMemoryRepository.Rejection(
                promotions.brandOf(publication.getPromotionId()), publication.getPromotionId(), id,
                com.socialhub.publishing.domain.EditorialMemoryRepository.Kind.TEXT, reason, blank(note),
                publication.getTitle(), discarded, clock.instant()));
        publications.save(publication);
        events.publishEvent(new FactoryChanged("publication", id));
        return publication;
    }

    private static @Nullable String blank(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
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
            if (quality.openIssues(p.getId()) > 0) {
                // No se aprueba sola: le quedan avisos de calidad
                p.flag(QUALITY_FLAG);
                publications.save(p);
                continue;
            }
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
