package com.socialhub.publishing.domain;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Dónde dejar las imágenes para que Meta las pueda descargar (Instagram solo publica imágenes desde una URL pública).
 * La implementación usa la carpeta pública de Drive: SocialHub copia ahí el JPEG, Claude (en la nube) obtiene su enlace
 * y SocialHub lo borra al terminar.
 */
@SecondaryPort
public interface PublicMediaHost {

    /** Hay dónde dejar imágenes para la marca cuya carpeta de «Encargos» es esa. */
    boolean available(String ordersFolder);

    /**
     * Copia la imagen (la convierte a JPEG si hace falta) al espacio público.
     *
     * @return el nombre del archivo público; vacío si no hay dónde dejarlo
     * @throws IllegalArgumentException si la imagen no se puede preparar (formato no admitido, no se lee…)
     */
    Optional<String> stage(String ordersFolder, Path image);

    /** Borra lo que se dejó (los que ya no están, se ignoran). */
    void release(String ordersFolder, Collection<String> names);

    /** Por qué la URL no sirve (no responde o no es una imagen); vacío si responde con una imagen. */
    Optional<String> problem(URI url);
}
