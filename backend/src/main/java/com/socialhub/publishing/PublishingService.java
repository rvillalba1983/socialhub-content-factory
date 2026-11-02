package com.socialhub.publishing;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
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
    private final PublicationFileRepository files;

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

    /** Asocia a mano un archivo de las carpetas de la promoción. */
    @Transactional
    public PublicationFile attachManually(String publicationId, String path) {
        Publication publication = find(publicationId);
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
        find(publicationId);
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
