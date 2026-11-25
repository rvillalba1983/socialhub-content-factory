package com.socialhub.settings;

import java.nio.file.Path;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Ajuste «Carpeta de audio de Litoques»: la carpeta local con los MP3 de los temas del disco, de donde SocialHub saca el
 * que mezcla en un reel o clip cuando la publicación pide «tema de Litoques en el vídeo» (decisiones 21, 23 y 58).
 */
@PrimaryPort
public interface AudioSettings {

    /** @param folder ruta guardada, si hay; @param available si existe ahora mismo como carpeta en este ordenador */
    record View(@Nullable String folder, boolean available) {}

    /** La carpeta guardada, sea cual sea su estado (puede no existir ahora). */
    Optional<Path> litoquesFolder();

    View view();

    /** Guarda la carpeta (ruta completa); vacía o {@code null} quita el ajuste. Una ruta que no es completa es un error. */
    View update(@Nullable String folder);
}
