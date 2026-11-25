package com.socialhub.pieces.adapter.out.files;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.socialhub.pieces.domain.PromoSpec;
import java.util.List;
import java.util.Map;
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
                 @Nullable Double inicio, @Nullable Double duracion,
                 @JsonProperty("fondo_svg") @Nullable String fondoSvg, @Nullable String plantilla,
                 @Nullable Map<String, String> campos, @Nullable Integer pagina, @Nullable Sound sonido) {

        PromoSpec.Piece toDomain() {
            return new PromoSpec.Piece(tipo, nombre, dia, publicacion, caption, hashtags,
                    slides == null ? null : slides.stream().map(Slide::toDomain).toList(),
                    cierre == null ? null : cierre.toDomain(), img, titulo, texto, cta, crop, gancho, versos, finalLines,
                    audio, audioInicio, video, inicio, duracion, fondoSvg, plantilla, campos, pagina,
                    sonido == null ? null : sonido.toDomain());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Slide(@Nullable String img, @Nullable String texto, @Nullable String titulo, @Nullable List<Double> crop,
                 @JsonProperty("fondo_svg") @Nullable String fondoSvg, @Nullable Integer pagina) {

        PromoSpec.Slide toDomain() { return new PromoSpec.Slide(img, texto, titulo, crop, fondoSvg, pagina); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Closing(@Nullable String img, List<String> lineas, @Nullable String plantilla,
                   @Nullable Map<String, String> campos, @Nullable Integer pagina) {

        PromoSpec.Closing toDomain() { return new PromoSpec.Closing(img, lineas, plantilla, campos, pagina); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sound(@Nullable String modo, @Nullable String tema) {

        PromoSpec.Sound toDomain() { return new PromoSpec.Sound(modo == null ? "" : modo, tema); }
    }

    PromoSpec toDomain() {
        return new PromoSpec(out, leeme, tituloLeeme, piezas == null ? null : piezas.stream().map(Piece::toDomain).toList());
    }
}
