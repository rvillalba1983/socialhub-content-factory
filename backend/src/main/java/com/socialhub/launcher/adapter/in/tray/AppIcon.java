package com.socialhub.launcher.adapter.in.tray;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** El icono de SocialHub (un cuadrado redondeado oscuro con la «S» clara, como la web), dibujado al tamaño que pida la bandeja. */
final class AppIcon {

    private AppIcon() {}

    static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x0D1117));
            int arc = Math.max(4, size / 4);
            g.fillRoundRect(0, 0, size, size, arc, arc);
            g.setColor(new Color(0xEEF1F5));
            g.setStroke(new BasicStroke(Math.max(1f, size / 16f)));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * 0.68f)));
            var metrics = g.getFontMetrics();
            int x = (size - metrics.stringWidth("S")) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString("S", x, y);
        } finally {
            g.dispose();
        }
        return image;
    }
}
