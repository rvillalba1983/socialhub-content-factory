package com.socialhub.pieces.domain;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * spec.json de piezas ({@code contracts/promo-spec.v1.schema.json}), con los mismos nombres que en promo.py. El
 * paso del JSON a este objeto lo hace el adaptador que lee el archivo.
 */
public record PromoSpec(@Nullable String out, @Nullable String leeme, @Nullable String tituloLeeme, List<Piece> piezas) {

    /** Tipos de pieza que SocialHub genera hoy. */
    public static final java.util.Set<String> ACTIVE_TYPES =
            java.util.Set.of("carrusel", "historia", "reel", "clip", "comprimir", "cita");

    /** Tipos que el contrato admite pero SocialHub aún no genera (no están activos para ninguna marca). */
    public static final java.util.Set<String> RESERVED_TYPES = java.util.Set.of("infografia", "meme");

    /**
     * Sonido de la publicación (decisión 23).
     *
     * @param mode  {@code none}, {@code litoques_track} o {@code network}
     * @param track nombre del tema (obligatorio salvo con {@code none})
     */
    public record Sound(String mode, @Nullable String track) {}

    /**
     * Una escena de un reel o clip montado con escenas.
     *
     * @param plantilla  plantilla del kit aprobado («historia» o «cita»), si la lleva
     * @param campos     textos de la plantilla (o {@code titulo} y {@code texto} si no hay plantilla)
     * @param img        imagen de la escena; sin ella, el fondo de la marca
     * @param duracionS  segundos que dura
     * @param transition cómo entra desde la escena anterior: {@code corte} (por defecto) o {@code fundido}; en la primera no se usa
     */
    public record Scene(@Nullable String plantilla, @Nullable Map<String, String> campos, @Nullable String img, double duracionS,
                        @Nullable String transition) {

        public static final String CUT = "corte";
        public static final String FADE = "fundido";

        public boolean fade() { return FADE.equals(transition); }
    }

    /**
     * @param plantilla identificador de una plantilla del kit aprobado (historia y cita; en el carrusel va en el cierre)
     * @param campos    textos de la plantilla: campo → texto
     * @param pagina    página del PDF de la que sale la imagen de la pieza
     * @param sonido    sonido de la publicación
     */
    public record Piece(String tipo, String nombre, @Nullable String dia, @Nullable String publicacion, @Nullable String caption,
                        @Nullable String hashtags, @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img,
                        @Nullable String titulo, @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop,
                        @Nullable String gancho, @Nullable List<String> versos, @Nullable List<String> finalLines,
                        @Nullable String audio, @Nullable Double audioInicio, @Nullable String video, @Nullable Double inicio,
                        @Nullable Double duracion, @Nullable String fondoSvg, @Nullable String plantilla,
                        @Nullable Map<String, String> campos, @Nullable Integer pagina, @Nullable Sound sonido,
                        @Nullable List<Scene> escenas) {

        /** Una pieza sin escenas (el spec de antes del bloque 5). */
        public Piece(String tipo, String nombre, @Nullable String dia, @Nullable String publicacion, @Nullable String caption,
                     @Nullable String hashtags, @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img,
                     @Nullable String titulo, @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop,
                     @Nullable String gancho, @Nullable List<String> versos, @Nullable List<String> finalLines,
                     @Nullable String audio, @Nullable Double audioInicio, @Nullable String video, @Nullable Double inicio,
                     @Nullable Double duracion, @Nullable String fondoSvg, @Nullable String plantilla,
                     @Nullable Map<String, String> campos, @Nullable Integer pagina, @Nullable Sound sonido) {
            this(tipo, nombre, dia, publicacion, caption, hashtags, slides, cierre, img, titulo, texto, cta, crop, gancho,
                    versos, finalLines, audio, audioInicio, video, inicio, duracion, fondoSvg, plantilla, campos, pagina, sonido, null);
        }

        /** Una pieza sin plantilla, página ni sonido (el spec de antes de la fase 13). */
        public Piece(String tipo, String nombre, @Nullable String dia, @Nullable String publicacion, @Nullable String caption,
                     @Nullable String hashtags, @Nullable List<Slide> slides, @Nullable Closing cierre, @Nullable String img,
                     @Nullable String titulo, @Nullable String texto, @Nullable String cta, @Nullable List<Double> crop,
                     @Nullable String gancho, @Nullable List<String> versos, @Nullable List<String> finalLines,
                     @Nullable String audio, @Nullable Double audioInicio, @Nullable String video, @Nullable Double inicio,
                     @Nullable Double duracion, @Nullable String fondoSvg) {
            this(tipo, nombre, dia, publicacion, caption, hashtags, slides, cierre, img, titulo, texto, cta, crop, gancho,
                    versos, finalLines, audio, audioInicio, video, inicio, duracion, fondoSvg, null, null, null, null, null);
        }
    }

    public record Slide(@Nullable String img, @Nullable String texto, @Nullable String titulo, @Nullable List<Double> crop,
                        @Nullable String fondoSvg, @Nullable Integer pagina) {

        public Slide(@Nullable String img, @Nullable String texto, @Nullable String titulo, @Nullable List<Double> crop,
                     @Nullable String fondoSvg) {
            this(img, texto, titulo, crop, fondoSvg, null);
        }
    }

    public record Closing(@Nullable String img, List<String> lineas, @Nullable String plantilla,
                          @Nullable Map<String, String> campos, @Nullable Integer pagina) {

        /** Un cierre con plantilla no necesita «lineas»: sin ellas, la lista queda vacía. */
        public Closing {
            lineas = lineas == null ? List.of() : lineas;
        }

        public Closing(@Nullable String img, List<String> lineas) {
            this(img, lineas, null, null, null);
        }
    }
}
