package com.socialhub.pieces;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** spec.json de piezas ({@code contracts/promo-spec.v1.schema.json}), con los mismos nombres que en promo.py. */
@JsonIgnoreProperties(ignoreUnknown = true)
record PromoSpec(@Nullable String out, @Nullable String leeme,
                 @JsonProperty("titulo_leeme") @Nullable String tituloLeeme, List<Piece> piezas) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Piece(String tipo, String nombre, @Nullable String dia, @Nullable String caption, @Nullable String hashtags,
                 @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img, @Nullable String titulo,
                 @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop, @Nullable String gancho,
                 @Nullable List<String> versos,
                 @JsonProperty("final") @Nullable List<String> finalLines, @Nullable String audio,
                 @JsonProperty("audio_inicio") @Nullable Double audioInicio, @Nullable String video,
                 @Nullable Double inicio, @Nullable Double duracion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Slide(String img, String texto, @Nullable String titulo, @Nullable List<Double> crop) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Closing(String img, List<String> lineas) {}
}
