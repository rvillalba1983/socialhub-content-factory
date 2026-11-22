package com.socialhub.pieces.adapter.out.svg;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ajusta un texto a una caja: lo parte en líneas y baja el tamaño hasta que cabe. Lógica pura: mide con
 * {@link TextLayout} y el mismo {@link #FRC} con el que luego se dibuja, para que medida y dibujo coincidan.
 */
final class TextFitter {

    /** Contexto común de medida y dibujo: suavizado y métricas fraccionarias. */
    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private static final String ELLIPSIS = "…";

    /** Tamaño elegido, líneas resultantes y si hubo que recortar el final. */
    record Result(int size, List<String> lines, boolean truncated) {}

    private TextFitter() {}

    /** La fuente base al tamaño dado, con tracking (el espaciado entre letras en unidades), kerning y ligaduras. */
    static Font derive(Font base, float size, double letterSpacing) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, size);
        attributes.put(TextAttribute.TRACKING, (float) (letterSpacing / size));
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        return base.deriveFont(attributes);
    }

    /** Ancho de una línea, sin el espacio de tracking que se añade tras la última letra. */
    static double width(String line, Font font) {
        if (line.isEmpty()) return 0;
        double advance = new TextLayout(line, font, FRC).getAdvance();
        Object tracking = font.getAttributes().get(TextAttribute.TRACKING);
        double trailing = tracking instanceof Number n ? n.doubleValue() * font.getSize2D() : 0;
        return advance - trailing;
    }

    static Result fit(String text, Font base, double boxWidth, double boxHeight, int maxSize, int minSize,
            double lineHeight, int maxLines, double letterSpacing) {
        int min = Math.min(minSize, maxSize);
        List<String> plain = wrap(text, derive(base, maxSize, letterSpacing), boxWidth, false);
        if (plain.isEmpty()) return new Result(maxSize, List.of(), false);
        for (int size = maxSize; size >= min; size--) {
            Font font = derive(base, size, letterSpacing);
            List<String> lines = wrap(text, font, boxWidth, false);
            if (lines.size() <= maxLines && lines.size() * lineHeight * size <= boxHeight
                    && lines.stream().allMatch(l -> width(l, font) <= boxWidth)) {
                return new Result(size, lines, false);
            }
        }
        Font font = derive(base, min, letterSpacing);
        List<String> lines = wrap(text, font, boxWidth, true);
        int fitting = (int) Math.floor(boxHeight / (lineHeight * min));
        int allowed = Math.max(1, Math.min(maxLines, fitting));
        if (lines.size() <= allowed) return new Result(min, lines, false);
        List<String> kept = new ArrayList<>(lines.subList(0, allowed));
        kept.set(allowed - 1, ellipsize(kept.get(allowed - 1), font, boxWidth));
        return new Result(min, kept, true);
    }

    /** Termina la línea con «…» quitando letras hasta que quepa en el ancho. */
    private static String ellipsize(String line, Font font, double boxWidth) {
        String head = line.stripTrailing();
        while (!head.isEmpty() && width(head + ELLIPSIS, font) > boxWidth) {
            head = head.substring(0, head.offsetByCodePoints(head.length(), -1)).stripTrailing();
        }
        return head + ELLIPSIS;
    }

    /**
     * Parte el texto en líneas: por {@code \n}, y cada párrafo por palabras. Las líneas en blanco intermedias se
     * conservan; las de los extremos se quitan. Con {@code splitLong}, las palabras más anchas que la caja se parten
     * por caracteres.
     */
    static List<String> wrap(String text, Font font, double boxWidth, boolean splitLong) {
        List<String> lines = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : normalized.split("\n", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.strip().split("\\s+")) {
                if (splitLong && width(word, font) > boxWidth) {
                    if (!current.isEmpty()) lines.add(current.toString());
                    List<String> chunks = splitWord(word, font, boxWidth);
                    lines.addAll(chunks.subList(0, chunks.size() - 1));
                    current = new StringBuilder(chunks.get(chunks.size() - 1));
                } else if (current.isEmpty()) {
                    current.append(word);
                } else if (width(current + " " + word, font) <= boxWidth) {
                    current.append(' ').append(word);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
            lines.add(current.toString());
        }
        while (!lines.isEmpty() && lines.get(0).isEmpty()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return lines;
    }

    private static List<String> splitWord(String word, Font font, double boxWidth) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < word.length(); ) {
            int cp = word.codePointAt(i);
            i += Character.charCount(cp);
            String next = current + new String(Character.toChars(cp));
            if (!current.isEmpty() && width(next, font) > boxWidth) {
                chunks.add(current.toString());
                current = new StringBuilder(new String(Character.toChars(cp)));
            } else {
                current = new StringBuilder(next);
            }
        }
        chunks.add(current.toString());
        return chunks;
    }
}
