package com.socialhub.brandkit;

import com.socialhub.brandkit.domain.ImageArea;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.brandkit.domain.PaletteColor;
import java.util.ArrayList;
import java.util.List;

/** Kits de ejemplo para las pruebas: el de El DeLito (cita, cierre e historia) y piezas para fabricar variantes malas. */
public final class KitFixtures {

    public static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    private KitFixtures() {}

    /** Un {@code <text>} con su caja; {@code attrs} añade o completa atributos del campo. */
    public static String field(String name, int x, int y, int w, int h, String attrs, String example) {
        return "<rect id=\"caja-" + name + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\" fill=\"none\"/>"
                + "<text id=\"campo-" + name + "\" " + attrs + ">" + example + "</text>";
    }

    public static final String CITA_ATTRS = "font-family=\"Crimson Text\" font-style=\"italic\" font-size=\"84\" data-tamano-min=\"44\" "
            + "data-interlineado=\"1.25\" data-vertical=\"centro\" text-anchor=\"middle\" fill=\"#F4EBDD\"";
    public static final String AUTHOR_ATTRS = "font-family=\"Fira Sans\" font-weight=\"500\" font-size=\"40\" letter-spacing=\"4\" "
            + "text-anchor=\"middle\" fill=\"#C9A25B\"";
    public static final String TITLE_ATTRS = "font-family=\"Bebas Neue\" font-size=\"110\" data-tamano-min=\"60\" "
            + "data-vertical=\"abajo\" text-anchor=\"middle\" fill=\"#F4EBDD\"";
    public static final String TEXT_ATTRS = "font-family=\"Crimson Text\" font-size=\"56\" data-tamano-min=\"34\" "
            + "text-anchor=\"middle\" fill=\"#F4EBDD\"";
    public static final String CTA_ATTRS = "font-family=\"Fira Sans\" font-weight=\"700\" font-size=\"44\" data-tamano-min=\"28\" "
            + "text-anchor=\"middle\" fill=\"#1F1A17\"";

    /** Lienzo oscuro con un marco dorado, con los campos dentro. */
    public static String canvas(int w, int h, String... fields) {
        return "<svg " + NS + " viewBox=\"0 0 " + w + " " + h + "\">"
                + "<rect width=\"" + w + "\" height=\"" + h + "\" fill=\"#1F1A17\"/>"
                + "<rect x=\"60\" y=\"60\" width=\"" + (w - 120) + "\" height=\"" + (h - 120) + "\" fill=\"none\" stroke=\"#C9A25B\" stroke-width=\"4\"/>"
                + String.join("", fields) + "</svg>";
    }

    public static String cita(int w, int h) {
        return canvas(w, h,
                field("cita", 120, 260, w - 240, h - 700, CITA_ATTRS, "Aquí va el verso"),
                field("autor", 120, h - 300, w - 240, 80, AUTHOR_ATTRS, "AUTOR"));
    }

    public static String cierre(int w, int h) {
        return canvas(w, h,
                field("titulo", 120, h / 2, w - 240, 200, TITLE_ATTRS, "TÍTULO"),
                field("texto", 120, h / 2 + 240, w - 240, 260, TEXT_ATTRS, "Texto de cierre"));
    }

    public static String historia(int w, int h) {
        return canvas(w, h,
                field("titulo", 120, h / 2 - 200, w - 240, 200, TITLE_ATTRS, "TÍTULO"),
                field("texto", 120, h / 2 + 40, w - 240, 300, TEXT_ATTRS, "Texto de la historia"),
                field("cta", 120, h - 340, w - 240, 90, CTA_ATTRS, "Llamada a la acción"));
    }

    public static KitTemplate template(String id, KitPiece piece, String svg, ImageArea image) {
        return new KitTemplate(id, piece, "Plantilla " + id, svg, image);
    }

    public static List<PaletteColor> palette() {
        return List.of(new PaletteColor("Tinta", "#1F1A17"), new PaletteColor("Oro viejo", "#C9A25B"),
                new PaletteColor("Papel", "#F4EBDD"), new PaletteColor("Rojo DeLito", "#D23A2E"));
    }

    public static List<KitTemplate> delitoTemplates() {
        return new ArrayList<>(List.of(
                template("cita-4x5", KitPiece.CITA, cita(1080, 1350), null),
                template("cita-9x16", KitPiece.CITA, cita(1080, 1920), null),
                template("cierre-4x5", KitPiece.CIERRE, cierre(1080, 1350), new ImageArea(0, 0, 1080, 700)),
                template("cierre-9x16", KitPiece.CIERRE, cierre(1080, 1920), new ImageArea(0, 0, 1080, 1000)),
                template("historia-9x16", KitPiece.HISTORIA, historia(1080, 1920), new ImageArea(0, 0, 1080, 900))));
    }

    /** El kit de El DeLito: cita (4:5 y 9:16), cierre del carrusel (4:5 y 9:16) e historia (9:16). */
    public static KitContent delito() {
        return new KitContent(palette(), "Marco dorado sobre tinta; la ilustración del libro va arriba.", delitoTemplates());
    }

    public static KitContent with(List<KitTemplate> templates) { return new KitContent(palette(), null, templates); }
}
