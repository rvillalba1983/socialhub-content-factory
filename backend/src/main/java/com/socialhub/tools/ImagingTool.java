package com.socialhub.tools;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;

/** Composición de imágenes con Java 2D (sustituye a Pillow en promo.py): no hay que instalar nada. */
@Component
public class ImagingTool implements LocalTool {

    @Override public String id() { return "java2d"; }
    @Override public String name() { return "Imágenes (Java 2D)"; }
    @Override public String description() { return "Imagen: recorte, composición de textos y exportación JPG"; }
    @Override public String license() { return "Incluido en Java"; }
    @Override public List<String> capabilities() { return List.of("carrusel", "historia"); }

    @Override
    public Check check() {
        try {
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setFont(new Font(Font.SERIF, Font.BOLD, 20));
            g.drawString("Lito", 4, 40);
            g.dispose();
            return new Check(true, "Java " + Runtime.version().feature() + " · composición sin pantalla");
        } catch (RuntimeException | Error e) {
            return new Check(false, "No funciona: " + e.getMessage());
        }
    }
}
