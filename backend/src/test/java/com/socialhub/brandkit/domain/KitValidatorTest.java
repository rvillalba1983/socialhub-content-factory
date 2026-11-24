package com.socialhub.brandkit.domain;

import static com.socialhub.brandkit.KitFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.brandkit.KitTemplateChecker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KitValidatorTest {

    private final KitValidator validator = new KitValidator(new KitTemplateChecker());

    private List<String> errorsOf(KitContent kit) { return validator.validate(kit); }

    /** Con la zona de imagen que exigen el cierre y la historia, para probar lo demás. */
    private List<String> errorsOf(KitPiece piece, String svg) {
        return errorsOf(with(List.of(template("t", piece, svg, piece.imageRequired() ? new ImageArea(0, 0, 1080, 600) : null))));
    }

    @Test
    void delitoKitIsValid() {
        assertThat(errorsOf(delito())).isEmpty();
    }

    @Test
    void optionalAuthorMayBeMissingAndFixedTextsAreAllowed() {
        String withoutAuthor = canvas(1080, 1350, field("cita", 120, 260, 840, 620, CITA_ATTRS, "Verso"));
        assertThat(errorsOf(KitPiece.CITA, withoutAuthor)).isEmpty();
        String withBrand = canvas(1080, 1350, field("cita", 120, 260, 840, 620, CITA_ATTRS, "Verso"),
                field("marca", 120, 1100, 840, 60, AUTHOR_ATTRS + " data-fijo=\"true\"", "EL DELITO"));
        assertThat(errorsOf(KitPiece.CITA, withBrand)).isEmpty();
    }

    @Test
    void paletteNeedsThreeToEightValidColors() {
        List<PaletteColor> two = palette().subList(0, 2);
        assertThat(errorsOf(new KitContent(two, null, delitoTemplates()))).containsExactly("La paleta debe tener entre 3 y 8 colores (tiene 2).");
        List<PaletteColor> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) nine.add(new PaletteColor("c" + i, "#00000" + i));
        assertThat(errorsOf(new KitContent(nine, null, delitoTemplates()))).anyMatch(e -> e.contains("entre 3 y 8"));
        List<PaletteColor> bad = new ArrayList<>(palette());
        bad.set(1, new PaletteColor("Oro", "#C9A"));
        assertThat(errorsOf(new KitContent(bad, null, delitoTemplates()))).containsExactly("El color «Oro» tiene un valor no válido («#C9A»): usa #RRGGBB.");
    }

    @Test
    void notesAreLimitedTo1000Characters() {
        assertThat(errorsOf(new KitContent(palette(), "x".repeat(1000), delitoTemplates()))).isEmpty();
        assertThat(errorsOf(new KitContent(palette(), "x".repeat(1001), delitoTemplates()))).containsExactly("La nota para Raúl supera los 1000 caracteres.");
    }

    @Test
    void templateCountAndIdsAreChecked() {
        assertThat(errorsOf(with(List.of()))).anyMatch(e -> e.contains("entre 1 y 12 plantillas"));
        List<KitTemplate> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add(template("cita-" + i, KitPiece.CITA, cita(1080, 1350), null));
        assertThat(errorsOf(with(many))).containsExactly("El kit debe tener entre 1 y 12 plantillas (tiene 13).");
        List<KitTemplate> repeated = List.of(template("cita-4x5", KitPiece.CITA, cita(1080, 1350), null),
                template("cita-4x5", KitPiece.CITA, cita(1080, 1920), null));
        assertThat(errorsOf(with(repeated))).containsExactly("El id de plantilla «cita-4x5» está repetido.");
        for (String id : List.of("Cita 4x5", "cita_4x5", "-cita", "cita-", "cita--4", "a".repeat(41), "")) {
            assertThat(errorsOf(with(List.of(template(id, KitPiece.CITA, cita(1080, 1350), null)))))
                    .as("id «%s»", id).anyMatch(e -> e.contains("id no válido"));
        }
    }

    @Test
    void inactivePiecesAreRejected() {
        assertThat(errorsOf(KitPiece.INFOGRAFIA, cita(1080, 1350))).containsExactly(
                "La plantilla «t» es de la pieza «infografia», que todavía no está activa (piezas activas: cita, cierre, historia).");
        assertThat(errorsOf(KitPiece.MEME, cita(1080, 1350))).hasSize(1).first().asString().contains("«meme»");
    }

    @Test
    void requiredFieldsOfEachPieceAreEnforced() {
        assertThat(errorsOf(KitPiece.CITA, cierre(1080, 1350))).anyMatch(e -> e.contains("necesita el campo «campo-cita»"));
        String onlyTitle = canvas(1080, 1350, field("titulo", 120, 600, 840, 200, TITLE_ATTRS, "T"));
        assertThat(errorsOf(KitPiece.CIERRE, onlyTitle)).containsExactly(
                "La plantilla «t» (cierre) necesita el campo «campo-texto» (y su «caja-texto»).");
        String noCta = canvas(1080, 1920, field("titulo", 120, 700, 840, 200, TITLE_ATTRS, "T"), field("texto", 120, 1000, 840, 300, TEXT_ATTRS, "x"));
        assertThat(errorsOf(KitPiece.HISTORIA, noCta)).containsExactly("La plantilla «t» (historia) necesita el campo «campo-cta» (y su «caja-cta»).");
    }

    @Test
    void aRequiredFieldCannotBeFixed() {
        String fixed = canvas(1080, 1350, field("cita", 120, 260, 840, 620, CITA_ATTRS + " data-fijo=\"true\"", "Verso"));
        assertThat(errorsOf(KitPiece.CITA, fixed)).anyMatch(e -> e.contains("como fijo"));
    }

    @Test
    void unknownNonFixedFieldsAreRejected() {
        String extra = canvas(1080, 1350, field("cita", 120, 260, 840, 620, CITA_ATTRS, "Verso"),
                field("fecha", 120, 1100, 840, 60, AUTHOR_ATTRS, "HOY"));
        assertThat(errorsOf(KitPiece.CITA, extra)).hasSize(1).first().asString()
                .contains("«campo-fecha», que esa pieza no usa").contains("data-fijo");
    }

    @Test
    void ratioMustMatchThePiece() {
        assertThat(errorsOf(KitPiece.CITA, cita(1080, 1080))).anyMatch(e -> e.contains("1080×1080") && e.contains("4:5 o 9:16"));
        // La historia solo admite 9:16
        assertThat(errorsOf(KitPiece.HISTORIA, historia(1080, 1350))).anyMatch(e -> e.contains("proporción admitida: 9:16"));
        // Otras medidas con la misma proporción valen (el motor las pinta a cualquier tamaño)
        String half = canvas(540, 675, field("cita", 60, 130, 420, 300, CITA_ATTRS.replace("84", "42").replace("44", "22"), "Verso"));
        assertThat(errorsOf(KitPiece.CITA, half)).isEmpty();
        assertThat(errorsOf(KitPiece.CITA, cita(1080, 1920))).isEmpty();
    }

    @Test
    void imageAreaMustFitInsideTheCanvas() {
        String svg = cita(1080, 1350);
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CITA, svg, new ImageArea(0, 0, 1080, 1350)))))).isEmpty();
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CITA, svg, new ImageArea(0, 0, 1200, 900)))))).anyMatch(e -> e.contains("fuera del lienzo"));
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CITA, svg, new ImageArea(-5, 0, 100, 100)))))).anyMatch(e -> e.contains("fuera del lienzo"));
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CITA, svg, new ImageArea(0, 0, 0, 100)))))).anyMatch(e -> e.contains("números no válidos"));
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CITA, svg, new ImageArea(0, 0, Double.NaN, 100)))))).anyMatch(e -> e.contains("números no válidos"));
    }

    @Test
    void engineErrorsAreReportedInSpanishWithTheTemplate() {
        String script = cita(1080, 1350).replace("</svg>", "<script>alert(1)</script></svg>");
        assertThat(errorsOf(KitPiece.CITA, script)).hasSize(1).first().asString()
                .startsWith("La plantilla «t» (cita): ").contains("no permitidos");
        String arial = cita(1080, 1350).replace("Fira Sans", "Arial");
        assertThat(errorsOf(KitPiece.CITA, arial)).anyMatch(e -> e.contains("Arial") && e.contains("no está incluida"));
        assertThat(errorsOf(KitPiece.CITA, "esto no es un svg")).anyMatch(e -> e.contains("XML"));
    }

    @Test
    void svgOverSixtyFourKilobytesIsRejected() {
        String big = cita(1080, 1350).replace("</svg>", "<desc>" + "é".repeat(33_000) + "</desc></svg>");
        assertThat(errorsOf(KitPiece.CITA, big)).containsExactly("La plantilla «t» supera el máximo de 64 KB.");
    }

    @Test
    void errorsOfSeveralTemplatesAreAllReturned() {
        List<KitTemplate> bad = List.of(template("a", KitPiece.CITA, cierre(1080, 1350), null),
                template("b", KitPiece.HISTORIA, historia(1080, 1350), null));
        assertThat(errorsOf(with(bad))).anyMatch(e -> e.startsWith("La plantilla «a»")).anyMatch(e -> e.startsWith("La plantilla «b»"));
    }

    @Test
    void closingAndStoryNeedTheImageAreaButTheQuoteMayHaveNone() {
        // Decisión 53: cierre e historia van encima de la ilustración y dicen dónde; la cita es verso sobre fondo
        assertThat(errorsOf(KitPiece.CITA, cita(1080, 1350))).isEmpty();
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CIERRE, cierre(1080, 1350), null))))).singleElement().asString()
                .startsWith("La plantilla «t» (cierre) necesita «image»").contains("encima de la ilustración");
        assertThat(errorsOf(with(List.of(template("t", KitPiece.HISTORIA, historia(1080, 1920), null))))).singleElement().asString()
                .contains("(historia) necesita «image»");
        assertThat(errorsOf(with(List.of(template("t", KitPiece.CIERRE, cierre(1080, 1350), new ImageArea(0, 0, 1080, 700)))))).isEmpty();
    }
}
