package com.socialhub.publishing.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Archivos asociados a cada publicación. */
@SecondaryPort
public interface PublicationFileRepository {

    /** Por orden de alta. */
    List<PublicationFile> files(String publicationId);

    /** Asocia el archivo. Si ya estaba asociado, no hace nada y devuelve {@code false}. */
    boolean add(String publicationId, PublicationFile file);

    /** Devuelve {@code false} si no estaba asociado. */
    boolean remove(String publicationId, String path);
}
