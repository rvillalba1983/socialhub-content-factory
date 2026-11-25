package com.socialhub.pieces.domain;

import java.awt.image.BufferedImage;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Pinta un fondo SVG que entrega Claude. Nunca falla: si el SVG no es seguro o no se puede pintar, devuelve vacío. */
@SecondaryPort
public interface SvgRasterizer {

    /** Tamaño máximo de un {@code fondo_svg}, en caracteres. */
    int MAX_CHARS = 64 * 1024;

    /** El SVG cumple las reglas de seguridad: solo formas de dibujo, sin script ni cargas externas. */
    boolean isSafe(String svg);

    /** El SVG pintado a ese tamaño en píxeles (RGB, sobre blanco), o vacío si no es seguro o falla. */
    Optional<BufferedImage> rasterize(String svg, int width, int height);

    /** Como {@link #rasterize} pero con transparencia (ARGB): lo que el SVG no dibuja queda transparente, para ponerlo sobre otra imagen. */
    Optional<BufferedImage> rasterizeTransparent(String svg, int width, int height);
}
