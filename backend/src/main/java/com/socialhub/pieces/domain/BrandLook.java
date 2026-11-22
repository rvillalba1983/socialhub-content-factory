package com.socialhub.pieces.domain;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Lo que el generador sabe de la marca para las piezas sin material propio: su color (#RRGGBB) y la portada genérica si
 * la hay. Sin color ({@code null}) la marca no genera fondos, ni los SVG de Claude ni los de SocialHub: no hay un color por
 * defecto.
 */
public record BrandLook(@Nullable String color, @Nullable Path cover) {

    public boolean hasColor() { return color != null && !color.isBlank(); }
}
