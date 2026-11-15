package com.socialhub.pieces.adapter.out.files;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.socialhub.pieces.domain.PromoSpec;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** El spec.json tal como está en el archivo ({@code contracts/promo-spec.v1.schema.json}), con sus nombres de campo. */
@JsonIgnoreProperties(ignoreUnknown = true)
record PromoSpecJson(@Nullable String out, @Nullable String leeme,
                     @JsonProperty("titulo_leeme") @Nullable String tituloLeeme, List<Piece> piezas) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Piece(String tipo, String nombre, @Nullable String dia, @Nullable String publicacion, @Nullable String caption,
                 @Nullable String hashtags, @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img,
                 @Nullable String titulo, @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop,
                 @Nullable String gancho, @Nullable List<String> versos,
                 @JsonProperty("final") @Nullable List<String> finalLines, @Nullable String audio,
                 @JsonProperty("audio_inicio") @Nullable Double audioInicio, @Nullable String video,
                 @Nullable Double inicio, @Nullable Double duracion) {

        PromoSpec.Piece toDomain() {
            return new PromoSpec.Piece(tipo, nombre, dia, publicacion, caption, hashtags,
                    slides == null ? null : slides.stream().map(Slide::toDomain).toList(),
                    cierre == null ? null : cierre.toDomain(), img, titulo, texto, cta, crop, gancho, versos, finalLines,
                    audio, audioInicio, video, inicio, duracion);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Slide(String img, String texto, @Nullable String titulo, @Nullable List<Double> crop) {

        PromoSpec.Slide toDomain() { return new PromoSpec.Slide(img, texto, titulo, crop); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Closing(String img, List<String> lineas) {

        PromoSpec.Closing toDomain() { return new PromoSpec.Closing(img, lineas); }
    }

    PromoSpec toDomain() {
        return new PromoSpec(out, leeme, tituloLeeme, piezas == null ? null : piezas.stream().map(Piece::toDomain).toList());
    }
}
