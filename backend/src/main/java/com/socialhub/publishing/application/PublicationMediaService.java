package com.socialhub.publishing.application;

import com.socialhub.platform.NotFoundException;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublicationMedia;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PublicationMediaService implements PublicationMedia {

    private static final String NOT_FOUND = "No se encuentra ese archivo en la publicación";

    private final PublicationRepository publications;
    private final PublicationFileRepository files;
    private final PromotionQueries promotions;

    @Override
    public Media open(String publicationId, String fileId) {
        var publication = publications.findById(publicationId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        PublicationFile file = files.files(publicationId).stream().filter(f -> f.id().equals(fileId)).findFirst()
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        String mime = file.mimeType();
        if (mime == null) throw new NotFoundException("Este tipo de archivo no se puede enseñar aquí");
        Path real;
        try {
            real = Path.of(file.path()).toRealPath();
        } catch (InvalidPathException | IOException e) {
            throw new NotFoundException("El archivo ya no está en este ordenador");
        }
        if (!Files.isRegularFile(real) || !insideFolders(publication.getPromotionId(), real)) throw new NotFoundException(NOT_FOUND);
        try {
            return new Media(real, mime, Files.size(real));
        } catch (IOException e) {
            throw new NotFoundException("El archivo ya no está en este ordenador");
        }
    }

    private boolean insideFolders(String promotionId, Path real) {
        for (String folder : promotions.driveFolders(promotionId)) {
            try {
                if (real.startsWith(Path.of(folder).toRealPath())) return true;
            } catch (InvalidPathException | IOException e) {
                // Carpeta no disponible en este ordenador
            }
        }
        return false;
    }
}
