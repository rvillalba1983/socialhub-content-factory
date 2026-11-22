package com.socialhub.pieces.domain;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Lo que el generador sabe de la marca para las piezas sin material propio: su color (#RRGGBB), la portada genérica
 * si la hay y, si la promoción lo tiene, su estilo visual (solo se usa para avisar y para el encargo a Claude).
 */
public record BrandLook(String color, @Nullable Path cover) {

    /** Rojo de la paleta de promo.py, para cuando no se conoce la marca. */
    public static final BrandLook DEFAULT = new BrandLook("#B22222", null);
}
