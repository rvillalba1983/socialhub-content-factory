package com.socialhub.pieces.application;

import java.nio.file.Path;

/** De dónde sale el MP3 de un tema de Litoques que hay que mezclar en un vídeo. */
@FunctionalInterface
interface LitoquesTracks {

    /** El MP3 del tema; {@code IllegalArgumentException} con el motivo en español si no se puede. */
    Path track(String name);
}
