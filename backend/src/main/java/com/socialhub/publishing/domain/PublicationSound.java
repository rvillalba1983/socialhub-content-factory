package com.socialhub.publishing.domain;

import java.util.Locale;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Sonido de una publicación (decisión 23): sin sonido, un tema de Litoques en el vídeo o un tema que se elige en la propia red.
 * Por ahora solo se guarda y se enseña; el montaje del audio es de otro bloque.
 *
 * @param mode  qué se pide
 * @param track nombre del tema (para que Raúl o Claude lo busquen); obligatorio salvo en {@link Mode#NONE}, donde no hay
 */
@ValueObject
public record PublicationSound(Mode mode, @Nullable String track) {

    public static final int MAX_TRACK = 200;
    public static final PublicationSound NONE = new PublicationSound(Mode.NONE, null);

    public enum Mode {
        /** «Sin sonido». */
        NONE,
        /** «Tema de Litoques en el vídeo». */
        LITOQUES_TRACK,
        /** «Elegir en la red: &lt;tema&gt;». */
        NETWORK;

        /** El nombre en el contrato y en el spec: {@code none}, {@code litoques_track}, {@code network}. */
        public String code() { return name().toLowerCase(Locale.ROOT); }

        public static Optional<Mode> fromCode(@Nullable String code) {
            if (code == null) return Optional.empty();
            for (Mode mode : values()) if (mode.code().equals(code.strip())) return Optional.of(mode);
            return Optional.empty();
        }
    }

    public PublicationSound {
        java.util.Objects.requireNonNull(mode);
        String name = track == null || track.isBlank() ? null : track.strip();
        if (mode == Mode.NONE) {
            // Sin sonido no hay tema: se descarta el que venga
            name = null;
        } else if (name == null) {
            throw new IllegalArgumentException("Falta el nombre del tema: con «" + label(mode, null) + "» hay que decir cuál es.");
        }
        if (name != null && name.length() > MAX_TRACK) {
            throw new IllegalArgumentException("El nombre del tema supera los " + MAX_TRACK + " caracteres.");
        }
        track = name;
    }

    /** Lee el modo por su nombre ({@code none}, {@code litoques_track}, {@code network}); si no existe, error en español. */
    public static PublicationSound of(@Nullable String mode, @Nullable String track) {
        Mode parsed = Mode.fromCode(mode).orElseThrow(() -> new IllegalArgumentException(
                "Sonido no válido «" + mode + "»: usa none (sin sonido), litoques_track (tema de Litoques) o network (elegir en la red)."));
        return new PublicationSound(parsed, track);
    }

    /** El texto que ve Raúl: «Sin sonido», «Tema de Litoques en el vídeo: X» o «Elegir en la red: X». */
    public String label() { return label(mode, track); }

    private static String label(Mode mode, @Nullable String track) {
        return switch (mode) {
            case NONE -> "Sin sonido";
            case LITOQUES_TRACK -> "Tema de Litoques en el vídeo" + (track == null ? "" : ": " + track);
            case NETWORK -> "Elegir en la red" + (track == null ? "" : ": " + track);
        };
    }
}
