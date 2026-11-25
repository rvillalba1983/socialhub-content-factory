package com.socialhub.pieces.application;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Lo que el generador necesita saber de fuera del spec: la marca (para sus plantillas aprobadas) y si una publicación es de
 * una red que pide 9:16 (TikTok). Sin marca no hay plantillas, y sin red conocida todo sale como siempre.
 *
 * @param brandId   marca de la promoción; {@code null}: sin plantillas
 * @param verticalNetwork si la publicación (su identificador) es de una red que pide 9:16 (hallazgo B)
 */
record GenerationSetup(@Nullable String brandId, Predicate<String> verticalNetwork) {

    /** Sin marca ni redes: las piezas salen exactamente como antes de la fase 13. */
    static final GenerationSetup NONE = new GenerationSetup(null, id -> false);
}
