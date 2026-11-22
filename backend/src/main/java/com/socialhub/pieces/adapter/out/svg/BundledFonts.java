package com.socialhub.pieces.adapter.out.svg;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las fuentes incluidas en la app ({@code /fonts}). Se cargan una sola vez y bajo demanda, sin registrarlas en el
 * sistema. Cada familia tiene los pesos y la cursiva que existen como archivo.
 */
final class BundledFonts {

    private record Variant(String file, int weight, boolean italic) {}

    private static final Map<String, List<Variant>> FAMILIES = Map.of(
            "Crimson Text", List.of(new Variant("CrimsonText-Regular.ttf", 400, false),
                    new Variant("CrimsonText-Italic.ttf", 400, true),
                    new Variant("CrimsonText-SemiBold.ttf", 600, false),
                    new Variant("CrimsonText-Bold.ttf", 700, false)),
            "Fira Sans", List.of(new Variant("FiraSans-Regular.ttf", 400, false),
                    new Variant("FiraSans-Italic.ttf", 400, true),
                    new Variant("FiraSans-Medium.ttf", 500, false),
                    new Variant("FiraSans-Bold.ttf", 700, false)),
            "Bebas Neue", List.of(new Variant("BebasNeue-Regular.ttf", 400, false)));

    private static final List<String> FAMILY_NAMES = List.of("Crimson Text", "Fira Sans", "Bebas Neue");

    private static final Map<String, Font> LOADED = new ConcurrentHashMap<>();

    private BundledFonts() {}

    /** Nombres de las familias disponibles, para los mensajes de error. */
    static List<String> families() {
        return FAMILY_NAMES;
    }

    /**
     * La fuente (tamaño 1) para esa familia, peso y estilo, o vacío si la familia no está incluida. La familia es el
     * primer elemento de la lista, sin comillas y sin distinguir mayúsculas; el peso elige el existente más cercano
     * (a igualdad, el más pesado); la cursiva solo si existe ese archivo (si no, la redonda).
     */
    static Optional<Font> resolve(String fontFamilyAttr, String fontWeight, String fontStyle) {
        String wanted = firstFamily(fontFamilyAttr);
        List<Variant> variants = null;
        for (var entry : FAMILIES.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(wanted)) variants = entry.getValue();
        }
        if (variants == null) return Optional.empty();
        boolean italic = fontStyle != null && fontStyle.strip().toLowerCase(Locale.ROOT).matches("italic|oblique");
        List<Variant> pool = new ArrayList<>(variants.stream().filter(v -> v.italic() == italic).toList());
        if (pool.isEmpty()) pool = new ArrayList<>(variants.stream().filter(v -> !v.italic()).toList());
        int target = weight(fontWeight);
        Variant best = pool.stream()
                .min(Comparator.comparingInt((Variant v) -> Math.abs(v.weight() - target)).thenComparing(Comparator.comparingInt(Variant::weight).reversed()))
                .orElseThrow();
        return Optional.of(LOADED.computeIfAbsent(best.file(), BundledFonts::load));
    }

    private static String firstFamily(String attr) {
        if (attr == null) return "";
        String first = attr.split(",", 2)[0].strip();
        return first.replaceAll("^['\"]|['\"]$", "").strip().toLowerCase(Locale.ROOT);
    }

    private static int weight(String attr) {
        if (attr == null || attr.isBlank()) return 400;
        String value = attr.strip().toLowerCase(Locale.ROOT);
        if (value.equals("normal")) return 400;
        if (value.equals("bold")) return 700;
        try {
            return Math.max(100, Math.min(900, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return 400;
        }
    }

    private static Font load(String file) {
        try (InputStream in = BundledFonts.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) throw new IllegalStateException("Falta la fuente incluida " + file);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | java.awt.FontFormatException e) {
            throw new IllegalStateException("No se pudo leer la fuente incluida " + file, e);
        }
    }
}
