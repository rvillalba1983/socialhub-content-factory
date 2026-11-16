package com.socialhub.pieces.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * spec.json de piezas ({@code contracts/promo-spec.v1.schema.json}), con los mismos nombres que en promo.py. El
 * paso del JSON a este objeto lo hace el adaptador que lee el archivo.
 */
public record PromoSpec(@Nullable String out, @Nullable String leeme, @Nullable String tituloLeeme, List<Piece> piezas) {

    public record Piece(String tipo, String nombre, @Nullable String dia, @Nullable String publicacion, @Nullable String caption,
                        @Nullable String hashtags, @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img,
                        @Nullable String titulo, @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop,
                        @Nullable String gancho, @Nullable List<String> versos, @Nullable List<String> finalLines,
                        @Nullable String audio, @Nullable Double audioInicio, @Nullable String video, @Nullable Double inicio,
                        @Nullable Double duracion) {}

    public record Slide(String img, String texto, @Nullable String titulo, @Nullable List<Double> crop) {}

    public record Closing(String img, List<String> lineas) {}
}
