package com.socialhub.pieces.application;

import static com.socialhub.pieces.application.Drawing.*;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Polygon;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.zip.CRC32;
import org.jspecify.annotations.Nullable;

/**
 * El fondo que dibuja SocialHub cuando una pieza no tiene material propio, ni SVG de Claude, ni portada de la marca:
 * una de siete plantillas (degradado, brillo, círculos, rayas, ondas, triángulos, puntos) con la paleta del color de la
 * marca, elegida y ajustada a partir del título. Misma entrada, mismo fondo; títulos distintos, fondos distintos.
 * El título va en grande sobre una banda oscura con contraste de lectura de al menos 7:1 (WCAG AAA).
 */
final class FallbackBackground {

    static final int TEMPLATES = 7;
    private static final Color TEXT = LIGHT;

    private FallbackBackground() {}

    /**
     * @param seedText el texto que decide la plantilla y sus ajustes (el título de la pieza)
     * @param title lo que se escribe en grande; nulo para un fondo sin texto (la pieza ya pone el suyo encima)
     * @param titleAt posición vertical del centro del título, de 0 a 1
     */
    static BufferedImage render(int w, int h, String colorHex, String seedText, @Nullable String title, double titleAt) {
        long seed = seed(seedText);
        Palette palette = Palette.of(colorHex);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        SplittableRandom rnd = new SplittableRandom(seed);
        switch (template(seedText)) {
            case 0 -> gradient(g, w, h, palette, rnd);
            case 1 -> glow(g, w, h, palette, rnd);
            case 2 -> circles(g, w, h, palette, rnd);
            case 3 -> stripes(g, w, h, palette, rnd);
            case 4 -> waves(g, w, h, palette, rnd);
            case 5 -> triangles(g, w, h, palette, rnd);
            default -> dots(g, w, h, palette, rnd);
        }
        if (title != null && !title.isBlank()) drawTitle(g, w, h, title.strip(), palette.panel(), titleAt);
        g.dispose();
        return image;
    }

    /** Número de plantilla (0 a 6) que sale de un título. */
    static int template(String seedText) {
        return (int) ((seed(seedText) >>> 8) % TEMPLATES);
    }

    private static long seed(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        CRC32 crc = new CRC32();
        crc.update(plain.getBytes(StandardCharsets.UTF_8));
        // CRC32 de dos pasadas para repartir mejor títulos parecidos
        long first = crc.getValue();
        crc.update(plain.getBytes(StandardCharsets.UTF_8));
        return (first << 32) ^ crc.getValue() ^ 0x9E3779B97F4A7C15L;
    }

    // ---------------------------------------------------------------- paleta

    /** Los colores derivados del de la marca. */
    record Palette(Color base, Color dark, Color light, Color accent, Color panel) {

        static Palette of(String hex) {
            Color base = parse(hex);
            float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
            float s = Math.max(hsb[1], 0.35f);
            float b = Math.max(hsb[2], 0.45f);
            Color dark = Color.getHSBColor(hsb[0], Math.min(1f, s), b * 0.45f);
            Color light = Color.getHSBColor(hsb[0], s * 0.55f, Math.min(1f, 0.55f + b * 0.45f));
            Color accent = Color.getHSBColor((hsb[0] + 0.08f) % 1f, Math.min(1f, s), Math.min(1f, b * 0.85f + 0.15f));
            return new Palette(base, dark, light, accent, readablePanel(base));
        }
    }

    /** Un color de la marca (#RRGGBB); si no se entiende, el rojo por defecto. */
    static Color parse(String hex) {
        try {
            return Color.decode(hex.strip());
        } catch (RuntimeException e) {
            return new Color(178, 34, 34);
        }
    }

    /** Oscurece el color de la marca hasta que el texto claro se lea con contraste de 7:1 como mínimo. */
    static Color readablePanel(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float brightness = Math.min(hsb[2], 0.5f);
        Color panel = Color.getHSBColor(hsb[0], Math.min(1f, Math.max(hsb[1], 0.3f)), brightness);
        while (contrast(TEXT, panel) < 7.0 && brightness > 0.02f) {
            brightness -= 0.02f;
            panel = Color.getHSBColor(hsb[0], Math.min(1f, Math.max(hsb[1], 0.3f)), Math.max(brightness, 0f));
        }
        return panel;
    }

    /** Contraste entre dos colores según WCAG (de 1 a 21). */
    static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    // ---------------------------------------------------------------- título

    private static void drawTitle(Graphics2D g, int w, int h, String title, Color panel, double at) {
        int margin = 80;
        int maxWidth = w - 2 * margin;
        Font font = bold(112);
        List<String> lines = wrap(g, title, font, maxWidth);
        for (int size = 112; size >= 48; size -= 4) {
            font = bold(size);
            lines = wrap(g, title, font, maxWidth);
            var metrics = g.getFontMetrics(font);
            boolean fits = lines.size() <= 4;
            for (String line : lines) if (metrics.stringWidth(line) > maxWidth) fits = false;
            if (fits) break;
        }
        int lh = font.getSize() + 18;
        int textH = lines.size() * lh;
        int pad = 56;
        int top = (int) Math.round(h * at - textH / 2.0) - pad;
        top = Math.max(0, Math.min(top, h - textH - 2 * pad));
        var previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        g.setColor(panel);
        g.fillRect(0, top, w, textH + 2 * pad);
        g.setComposite(previous);
        int y = top + pad;
        for (String line : lines) {
            centered(g, y, line, font, TEXT, w);
            y += lh;
        }
    }

    // ---------------------------------------------------------------- plantillas

    private static void gradient(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        boolean flip = rnd.nextBoolean();
        int drift = rnd.nextInt(w / 2);
        g.setPaint(new GradientPaint(flip ? w : 0, 0, p.dark(), flip ? 0 : w - drift, h, p.base()));
        g.fillRect(0, 0, w, h);
        g.setPaint(new GradientPaint(0, h * 0.55f, new Color(p.light().getRed(), p.light().getGreen(), p.light().getBlue(), 0),
                0, h, new Color(p.light().getRed(), p.light().getGreen(), p.light().getBlue(), 90)));
        g.fillRect(0, 0, w, h);
    }

    private static void glow(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setColor(p.dark());
        g.fillRect(0, 0, w, h);
        float cx = w * (0.2f + 0.6f * (float) rnd.nextDouble()), cy = h * (0.2f + 0.6f * (float) rnd.nextDouble());
        float radius = Math.max(w, h) * (0.55f + 0.3f * (float) rnd.nextDouble());
        g.setPaint(new RadialGradientPaint(cx, cy, radius, new float[] {0f, 0.55f, 1f},
                new Color[] {p.light(), p.base(), p.dark()}));
        g.fillRect(0, 0, w, h);
    }

    private static void circles(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setPaint(new GradientPaint(0, 0, p.base(), w, h, p.dark()));
        g.fillRect(0, 0, w, h);
        int n = 5 + rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            int r = (int) (Math.min(w, h) * (0.12 + 0.3 * rnd.nextDouble()));
            int x = rnd.nextInt(w), y = rnd.nextInt(h);
            Color c = i % 2 == 0 ? p.light() : p.accent();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55 + rnd.nextInt(50)));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
        }
    }

    private static void stripes(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setColor(p.base());
        g.fillRect(0, 0, w, h);
        int band = 70 + rnd.nextInt(90);
        boolean left = rnd.nextBoolean();
        g.setColor(new Color(p.dark().getRed(), p.dark().getGreen(), p.dark().getBlue(), 150));
        for (int k = -h; k < w + h; k += 2 * band) {
            Polygon stripe = new Polygon();
            int dir = left ? -1 : 1;
            stripe.addPoint(k, 0);
            stripe.addPoint(k + band, 0);
            stripe.addPoint(k + band + dir * h, h);
            stripe.addPoint(k + dir * h, h);
            g.fill(stripe);
        }
    }

    private static void waves(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setPaint(new GradientPaint(0, 0, p.dark(), 0, h, p.base()));
        g.fillRect(0, 0, w, h);
        int layers = 4 + rnd.nextInt(3);
        double phase = rnd.nextDouble() * Math.PI * 2;
        Color[] colors = {p.base(), p.accent(), p.light(), p.base(), p.accent(), p.light()};
        for (int i = 0; i < layers; i++) {
            double baseY = h * (0.25 + 0.7 * i / layers);
            double amp = 40 + rnd.nextInt(70), len = 260 + rnd.nextInt(380), ph = phase + i * 0.9;
            Path2D wave = new Path2D.Double();
            wave.moveTo(0, h);
            for (int x = 0; x <= w; x += 8) wave.lineTo(x, baseY + amp * Math.sin(2 * Math.PI * x / len + ph));
            wave.lineTo(w, h);
            wave.closePath();
            Color c = colors[i % colors.length];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 95));
            g.fill(wave);
        }
    }

    private static void triangles(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setColor(p.base());
        g.fillRect(0, 0, w, h);
        Color[] colors = {p.dark(), p.light(), p.accent()};
        int n = 14 + rnd.nextInt(10);
        for (int i = 0; i < n; i++) {
            Color c = colors[rnd.nextInt(colors.length)];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70 + rnd.nextInt(90)));
            Polygon t = new Polygon();
            int cx = rnd.nextInt(w), cy = rnd.nextInt(h), size = 140 + rnd.nextInt(300);
            for (int k = 0; k < 3; k++) t.addPoint(cx + rnd.nextInt(size) - size / 2, cy + rnd.nextInt(size) - size / 2);
            g.fill(t);
        }
    }

    private static void dots(Graphics2D g, int w, int h, Palette p, SplittableRandom rnd) {
        g.setPaint(new GradientPaint(0, 0, p.base(), w, h, p.dark()));
        g.fillRect(0, 0, w, h);
        int step = 70 + rnd.nextInt(50);
        int offset = rnd.nextInt(step);
        g.setColor(new Color(p.light().getRed(), p.light().getGreen(), p.light().getBlue(), 80));
        g.setStroke(new BasicStroke(1f));
        for (int y = offset; y < h + step; y += step) {
            for (int x = offset + ((y / step) % 2 == 0 ? 0 : step / 2); x < w + step; x += step) {
                int r = 6 + (int) (14 * (1.0 - (double) y / (h + step)));
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
            }
        }
    }
}
