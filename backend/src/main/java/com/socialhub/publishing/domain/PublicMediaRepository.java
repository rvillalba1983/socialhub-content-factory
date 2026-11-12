package com.socialhub.publishing.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Imágenes de una publicación dejadas en el espacio público y el enlace que Claude encontró para cada una. */
@SecondaryPort
public interface PublicMediaRepository {

    record Item(String publicationId, String sourcePath, String fileName, @Nullable String url) {}

    /** Sustituye las de la publicación (sin enlaces todavía). */
    void replace(String publicationId, List<Item> items);

    /** Por orden de alta. */
    List<Item> items(String publicationId);

    Optional<Item> byFileName(String fileName);

    void setUrl(String publicationId, String fileName, String url);

    void delete(String publicationId);
}
