package com.socialhub.pieces.domain;

import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Los MP3 de una carpeta de audio, para mezclar un tema de Litoques en un vídeo (decisiones 21, 23 y 58). */
@SecondaryPort
public interface AudioLibrary {

    /**
     * El MP3 de un tema, buscado por su nombre dentro de la carpeta.
     *
     * @throws IllegalArgumentException con el motivo en español si la carpeta no está disponible, no hay ningún archivo con
     *                                  ese nombre o hay varios que encajan
     */
    Path find(Path folder, String name);
}
