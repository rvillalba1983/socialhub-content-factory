package com.socialhub.publishing.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.OptionalInt;

/**
 * Busca una cita en el texto de un documento. Compara sin diferencias de espacios, saltos de línea, comillas, guiones,
 * ligaduras ni tildes; las mayúsculas sí cuentan (la cita es literal).
 */
public final class CitationMatcher {

    private CitationMatcher() {}

    /** Texto comparable. */
    public static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC).replace("\u00AD", "");
        t = t.replaceAll("[\\u201C\\u201D\\u201E\\u201F\\u00AB\\u00BB\\u2033]", "\"")
                .replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u00B4`]", "'")
                .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-")
                .replace("\u2026", "...");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t.replaceAll("\\s+", " ").strip();
    }

    /** El mismo texto con las palabras partidas por guion a final de línea ya unidas («pala- bra» → «palabra»). */
    private static String joinHyphenated(String raw) {
        return raw.replaceAll("(\\p{L})[-\u00AD]\\s*\\R\\s*(\\p{L})", "$1$2");
    }

    /**
     * @return el índice (0 = primera parte) de la primera parte donde aparece la cita; vacío si no aparece
     */
    public static OptionalInt find(List<String> parts, String citation) {
        String needle = normalize(citation);
        if (needle.isEmpty()) return OptionalInt.empty();
        for (int i = 0; i < parts.size(); i++) {
            String raw = parts.get(i);
            if (normalize(raw).contains(needle) || normalize(joinHyphenated(raw)).contains(needle)) return OptionalInt.of(i);
        }
        // Una cita que empieza en una página y acaba en la siguiente
        for (int i = 0; i + 1 < parts.size(); i++) {
            if (normalize(parts.get(i) + "\n" + parts.get(i + 1)).contains(needle)) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    private static final int WINDOW = 12;

    /** Número de línea (desde 1) donde empieza la cita dentro de un texto plano, o 0 si no se encuentra línea a línea. */
    public static int lineOf(String text, String citation) {
        String needle = normalize(citation);
        String[] lines = text.split("\\R", -1);
        int first = -1;
        for (int i = 0; i < lines.length && first < 0; i++) {
            if (window(lines, i).contains(needle)) first = i;
        }
        if (first < 0) return 0;
        // La cita empieza en la última línea desde la que todavía se encuentra entera
        int start = first;
        while (start + 1 < lines.length && start + 1 < first + WINDOW && window(lines, start + 1).contains(needle)) start++;
        return start + 1;
    }

    private static String window(String[] lines, int from) {
        return normalize(String.join("\n", java.util.Arrays.copyOfRange(lines, from, Math.min(lines.length, from + WINDOW))));
    }
}
