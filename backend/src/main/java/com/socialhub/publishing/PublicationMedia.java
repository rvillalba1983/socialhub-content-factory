package com.socialhub.publishing;

import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Archivos de una publicación listos para enseñarse (solo lectura). */
@PrimaryPort
public interface PublicationMedia {

    /** Un archivo ya comprobado: existe, es de la publicación y está dentro de las carpetas de su promoción. */
    record Media(Path path, String mimeType, long size) {}

    /**
     * Abre el archivo {@code fileId} de la publicación. La ruta sale siempre de lo asociado a la publicación en la base de
     * datos, nunca de lo que pida el cliente.
     *
     * @throws com.socialhub.platform.NotFoundException si no existe, no es de esa publicación, está fuera de las carpetas
     *         de la promoción o no es una imagen ni un vídeo
     */
    Media open(String publicationId, String fileId);
}
