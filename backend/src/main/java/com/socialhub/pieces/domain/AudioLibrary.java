package com.socialhub.pieces.domain;

import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Los temas de Litoques en MP3 que SocialHub puede mezclar en un vídeo (decisiones 21, 23 y 58). */
@SecondaryPort
public interface AudioLibrary {

    /**
     * El MP3 de un tema, buscado por su nombre.
     *
     * @throws IllegalArgumentException con el motivo en español si no hay carpeta de audio, no está disponible, no hay
     *                                  ningún archivo con ese nombre o hay varios que encajan
     */
    Path track(String name);
}
