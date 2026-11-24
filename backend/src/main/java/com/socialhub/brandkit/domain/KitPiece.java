package com.socialhub.brandkit.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pieza para la que hay plantilla. Cada una dice sus campos de texto obligatorios (nombre tras {@code campo-}), los
 * opcionales y las proporciones que admite. {@code INFOGRAFIA} y {@code MEME} las admite el contrato pero no están activas.
 */
public enum KitPiece {
    CITA(true, false, List.of("cita"), List.of("autor"), List.of(Ratio.R4X5, Ratio.R9X16)),
    CIERRE(true, true, List.of("titulo", "texto"), List.of(), List.of(Ratio.R4X5, Ratio.R9X16)),
    HISTORIA(true, true, List.of("titulo", "texto", "cta"), List.of(), List.of(Ratio.R9X16)),
    INFOGRAFIA(false, false, List.of(), List.of(), List.of()),
    MEME(false, false, List.of(), List.of(), List.of());

    /** Proporciones de lienzo que usa SocialHub. */
    public enum Ratio {
        R4X5("4:5", 4, 5), R9X16("9:16", 9, 16);

        private final String label;
        private final int w;
        private final int h;

        Ratio(String label, int w, int h) { this.label = label; this.w = w; this.h = h; }

        public String label() { return label; }

        /** Si un lienzo de ese tamaño tiene esta proporción (con el 1 % de tolerancia del motor de plantillas). */
        public boolean matches(int width, int height) {
            double expected = (double) w / h;
            double real = (double) width / height;
            return Math.abs(real - expected) / expected <= 0.01;
        }
    }

    private final boolean active;
    private final boolean imageRequired;
    private final List<String> required;
    private final List<String> optional;
    private final List<Ratio> ratios;

    KitPiece(boolean active, boolean imageRequired, List<String> required, List<String> optional, List<Ratio> ratios) {
        this.active = active;
        this.imageRequired = imageRequired;
        this.required = required;
        this.optional = optional;
        this.ratios = ratios;
    }

    public boolean active() { return active; }

    /**
     * La plantilla debe indicar dónde va la ilustración (decisión 53: cierre e historia van encima de la imagen de la
     * publicación). La cita es verso sobre fondo: puede no llevar imagen.
     */
    public boolean imageRequired() { return imageRequired; }

    public List<String> requiredFields() { return required; }

    public List<String> optionalFields() { return optional; }

    public List<Ratio> ratios() { return ratios; }

    /** Nombre en el contrato y en el JSON ({@code cita}, {@code infografia}…). */
    public String code() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<KitPiece> fromCode(String code) {
        for (KitPiece piece : values()) if (piece.code().equals(code)) return Optional.of(piece);
        return Optional.empty();
    }

    /** Los códigos de las piezas activas, para los mensajes: «cita, cierre, historia». */
    public static String activeCodes() {
        return String.join(", ", java.util.Arrays.stream(values()).filter(KitPiece::active).map(KitPiece::code).toList());
    }
}
