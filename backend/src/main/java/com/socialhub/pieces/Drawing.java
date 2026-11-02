package com.socialhub.pieces;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Las primitivas de dibujo de promo.py con Java 2D: misma paleta, misma tipografía (DejaVu Serif), mismos
 * tamaños y márgenes. Los textos se colocan como en Pillow: la {@code y} es la parte de arriba del texto.
 */
final class Drawing {

    static final Color CREAM = new Color(250, 244, 232);
    static final Color INK = new Color(40, 30, 22);
    static final Color RED = new Color(178, 34, 34);
    static final Color LIGHT = new Color(255, 248, 235);
    static final Color WARM = new Color(240, 210, 170);
    static final Color ACCENT = new Color(230, 120, 100);
    static final Color SHADE = new Color(20, 14, 8);

    private static final Font BOLD = load("/fonts/DejaVuSerif-Bold.ttf");
    private static final Font ITALIC = load("/fonts/DejaVuSerif-Italic.ttf");

    private Drawing() {}

    static Font bold(int size) { return BOLD.deriveFont((float) size); }

    static Font italic(int size) { return ITALIC.deriveFont((float) size); }

    private static Font load(String resource) {
        try (InputStream in = Drawing.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Falta la tipografía " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("Tipografía dañada: " + resource, e);
        }
    }

    static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("No es una imagen válida: " + path.getFileName());
        return rgb(image);
    }

    static BufferedImage rgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, Color.WHITE, null);
        g.dispose();
        return out;
    }

    static BufferedImage canvas(int w, int h, Color background) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    /** Escala para cubrir W×H y recorta en el punto (fx, fy), como {@code cover} de promo.py. */
    static BufferedImage cover(BufferedImage im, int w, int h, double fx, double fy) {
        double sc = Math.max((double) w / im.getWidth(), (double) h / im.getHeight());
        int nw = (int) (im.getWidth() * sc) + 1, nh = (int) (im.getHeight() * sc) + 1;
        BufferedImage scaled = scale(im, nw, nh);
        int x = (int) ((nw - w) * fx), y = (int) ((nh - h) * fy);
        return copy(scaled.getSubimage(x, y, w, h));
    }

    /** Escalado de calidad: en pasos de la mitad al reducir mucho (como el filtro LANCZOS de Pillow, aproximado). */
    static BufferedImage scale(BufferedImage im, int w, int h) {
        BufferedImage current = im;
        int cw = im.getWidth(), ch = im.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            current = draw(current, cw, ch);
        }
        return draw(current, w, h);
    }

    private static BufferedImage draw(BufferedImage im, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(out);
        g.drawImage(im, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage copy(BufferedImage image) {
        return draw(image, image.getWidth(), image.getHeight());
    }

    static List<String> wrap(Graphics2D g, String text, Font font, int maxWidth) {
        FontMetrics fm = g.getFontMetrics(font);
        List<String> out = new ArrayList<>();
        String current = "";
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                current = candidate;
            } else {
                if (!current.isEmpty()) out.add(current);
                current = word;
            }
        }
        if (!current.isEmpty()) out.add(current);
        return out;
    }

    /** Texto centrado; {@code y} es la parte de arriba, como en Pillow. */
    static void centered(Graphics2D g, int y, String text, Font font, Color color, int width) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2f, y + fm.getAscent());
    }

    /** Párrafo centrado con margen y separación entre líneas. Devuelve la {@code y} siguiente. */
    static int block(Graphics2D g, int y, String text, Font font, Color color, int width, int margin, int gap) {
        for (String line : wrap(g, text, font, width - 2 * margin)) {
            centered(g, y, line, font, color, width);
            y += font.getSize() + gap;
        }
        return y;
    }

    static int block(Graphics2D g, int y, String text, Font font, Color color, int width) {
        return block(g, y, text, font, color, width, 80, 18);
    }

    /** Rectángulo semitransparente (el sombreado de las historias y los reels). */
    static void shade(Graphics2D g, int x, int y, int w, int h, int alpha) {
        var previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
        g.setColor(SHADE);
        g.fillRect(x, y, w, h);
        g.setComposite(previous);
    }

    static Color withAlpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.max(0, Math.min(1, alpha))));
    }

    /** JPG con la calidad indicada (0–100), como {@code save(quality=…)} de Pillow. */
    static void writeJpg(BufferedImage image, Path file, int quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100f);
        Files.createDirectories(file.getParent());
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb(image), null, null), param);
        } finally {
            writer.dispose();
        }
    }

    static void writePng(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }
}
