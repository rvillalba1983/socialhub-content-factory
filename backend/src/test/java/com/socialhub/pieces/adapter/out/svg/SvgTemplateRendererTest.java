package com.socialhub.pieces.adapter.out.svg;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.pieces.domain.TemplateRender;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SvgTemplateRendererTest {

    private final SvgTemplateRenderer renderer = new SvgTemplateRenderer(new BatikSvgRasterizer());

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";
    /** Caja de la plantilla de pruebas: x=100, y=200, 600 de ancho y 300 de alto, en un lienzo de 1000×1000. */
    private static final Rectangle BOX = new Rectangle(100, 200, 600, 300);

    /** Lienzo negro de 1000×1000 con un solo campo blanco; {@code attrs} se añade a los atributos del campo. */
    private static String template(String attrs, String example) {
        return "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect width=\"1000\" height=\"1000\" fill=\"#000000\"/>"
                + "<rect id=\"caja-t\" x=\"100\" y=\"200\" width=\"600\" height=\"300\" fill=\"none\"/>"
                + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"80\" fill=\"#FFFFFF\" " + attrs + ">" + example + "</text></svg>";
    }

    private static String template(String attrs) {
        return template(attrs, "Ejemplo");
    }

    private TemplateRender draw(String svg, String text) {
        TemplateRender result = renderer.render(svg, Map.of("t", text), 1000, 1000);
        assertThat(result.errors()).isEmpty();
        return result;
    }

    /** Rectángulo que envuelve los píxeles claros, o nulo si no hay tinta. */
    private static Rectangle ink(BufferedImage image) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFF) > 128) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static void assertInsideBox(Rectangle ink) {
        assertThat(ink).isNotNull();
        assertThat(ink.x).isGreaterThanOrEqualTo(BOX.x - 2);
        assertThat(ink.y).isGreaterThanOrEqualTo(BOX.y - 2);
        assertThat(ink.x + ink.width).isLessThanOrEqualTo(BOX.x + BOX.width + 2);
        assertThat(ink.y + ink.height).isLessThanOrEqualTo(BOX.y + BOX.height + 2);
    }

    // ---------------------------------------------------------------- el texto nunca sale de su caja

    static Stream<Arguments> texts() {
        String verse = "Cuando el sol se esconde\ntras la sierra de Granada\n\ny la noche abre su capa\nvuelvo a casa sin prisa";
        String longText = "Lorem ipsum dolor sit amet consectetur adipiscing elit ".repeat(6);
        return Stream.of(
                Arguments.of("start", "Hola"), Arguments.of("middle", "Hola"), Arguments.of("end", "Hola"),
                Arguments.of("start", longText), Arguments.of("middle", longText), Arguments.of("end", longText),
                Arguments.of("start", verse), Arguments.of("middle", verse), Arguments.of("end", verse),
                Arguments.of("middle", "Anticonstitucionalmente".repeat(3)),
                Arguments.of("middle", "palabra ".repeat(500)));
    }

    @ParameterizedTest
    @MethodSource("texts")
    void theTextNeverLeavesItsBox(String anchor, String text) {
        var result = draw(template("text-anchor=\"" + anchor + "\" data-vertical=\"centro\" data-tamano-min=\"20\""), text);
        assertInsideBox(ink(result.image()));
    }

    @Test
    void trackingDoesNotPushTheTextOutOfTheBox() {
        var result = draw(template("letter-spacing=\"12\" text-anchor=\"end\" data-tamano-min=\"20\""), "Un texto con espaciado entre letras bastante largo");
        assertInsideBox(ink(result.image()));
    }

    @Test
    void longTextShrinksAndWrapsIntoSeveralLines() {
        String text = "Esto es un texto bastante largo que no cabe en una sola línea al tamaño máximo de la plantilla";
        Rectangle shortInk = ink(draw(template("data-tamano-min=\"20\""), "Hola").image());
        var result = draw(template("data-tamano-min=\"20\""), text);
        Rectangle longInk = ink(result.image());
        assertThat(result.warnings()).isEmpty();
        assertThat(longInk.height).isGreaterThan((int) (shortInk.height * 1.8));
        assertInsideBox(longInk);
    }

    @Test
    void hugeTextWarnsAboutTruncationAndStaysInsideTheBox() {
        var result = draw(template("data-tamano-min=\"30\""), "palabra ".repeat(200));
        assertThat(result.warnings()).singleElement().asString().contains("«t»").contains("recortado");
        assertInsideBox(ink(result.image()));
    }

    // ---------------------------------------------------------------- caracteres

    @Test
    void spanishCharactersAmpersandAndUrlsAreDrawnWithoutErrorsOrWarnings() {
        var result = draw(template("data-tamano-min=\"20\""), "Año nuevo: ¿Qué tal? ¡Sí! «hola» Rock & Roll https://ejemplo.es");
        assertThat(result.warnings()).isEmpty();
        assertThat(ink(result.image())).isNotNull();
    }

    @Test
    void anEmojiIsRemovedWithAGlyphWarningAndDoesNotBreakAnything() {
        var withEmoji = draw(template(""), "Hola 😀");
        assertThat(withEmoji.warnings()).singleElement().asString().contains("«t»").contains("sin glifo").contains("😀");
        assertInsideBox(ink(withEmoji.image()));
        var emojiOnly = draw(template(""), "😀");
        assertThat(emojiOnly.warnings()).hasSize(1);
        assertThat(ink(emojiOnly.image())).isNull();
    }

    // ---------------------------------------------------------------- alineación

    @Test
    void horizontalAlignmentPlacesTheInkAtTheCorrectEdge() {
        Rectangle start = ink(draw(template("text-anchor=\"start\""), "HIH").image());
        Rectangle middle = ink(draw(template("text-anchor=\"middle\""), "HIH").image());
        Rectangle end = ink(draw(template("text-anchor=\"end\""), "HIH").image());
        assertThat(start.x).isBetween(BOX.x, BOX.x + 10);
        assertThat(middle.x + middle.width / 2.0).isBetween(BOX.getCenterX() - 6, BOX.getCenterX() + 6);
        assertThat(end.x + end.width).isBetween(BOX.x + BOX.width - 10, BOX.x + BOX.width);
    }

    @Test
    void trailingTrackingDoesNotShiftTheRightAlignedInk() {
        Rectangle end = ink(draw(template("text-anchor=\"end\" letter-spacing=\"20\""), "HIH").image());
        assertThat(end.x + end.width).isBetween(BOX.x + BOX.width - 10, BOX.x + BOX.width);
    }

    @Test
    void verticalAlignmentPlacesTheBlockInsideTheBox() {
        Rectangle top = ink(draw(template("data-vertical=\"arriba\""), "HIH").image());
        Rectangle center = ink(draw(template("data-vertical=\"centro\""), "HIH").image());
        Rectangle bottom = ink(draw(template("data-vertical=\"abajo\""), "HIH").image());
        assertThat(top.y).isBetween(BOX.y, BOX.y + 40);
        assertThat(center.getCenterY()).isBetween(BOX.getCenterY() - 30, BOX.getCenterY() + 30);
        assertThat(bottom.y + bottom.height).isBetween(BOX.y + BOX.height - 60, BOX.y + BOX.height);
        assertThat(top.y).isLessThan(center.y);
        assertThat(center.y).isLessThan(bottom.y);
    }

    @Test
    void fillOpacityMakesTheInkDimmer() {
        var result = draw(template("fill-opacity=\"0.5\""), "HHH");
        assertThat(ink(result.image())).isNull(); // 50 % de blanco sobre negro queda por debajo del umbral claro
        var strong = draw(template("fill-opacity=\"1\""), "HHH");
        assertThat(ink(strong.image())).isNotNull();
    }

    // ---------------------------------------------------------------- fijo, vacío, sobrantes, vista previa

    @Test
    void fixedFieldAlwaysWritesTheTemplateTextAndIgnoresClaudesText() {
        var fixed = draw(template("data-fijo=\"true\"", "FIJO"), "otro texto muy distinto y mucho más largo");
        var reference = renderer.preview(template("data-fijo=\"true\"", "FIJO"), 1000, 1000);
        assertThat(ink(fixed.image())).isEqualTo(ink(reference.image()));
        assertThat(ink(fixed.image())).isNotNull();
    }

    @Test
    void aFieldWithoutTextIsEmptyAndNeverShowsTheExample() {
        TemplateRender result = renderer.render(template("", "Ejemplo"), Map.of(), 1000, 1000);
        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(ink(result.image())).isNull();
    }

    @Test
    void anExtraKeyIsAWarningAndTheRestIsStillDrawn() {
        TemplateRender result = renderer.render(template(""), Map.of("t", "Hola", "no_existe", "x"), 1000, 1000);
        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("«no_existe»");
        assertThat(ink(result.image())).isNotNull();
    }

    @Test
    void previewUsesTheExampleTextOfEachField() {
        TemplateRender preview = renderer.preview(template("", "Ejemplo"), 1000, 1000);
        assertThat(preview.ok()).isTrue();
        assertThat(ink(preview.image())).isNotNull();
        assertThat(ink(preview.image())).isEqualTo(ink(draw(template("", "Ejemplo"), "Ejemplo").image()));
    }

    // ---------------------------------------------------------------- validación

    static Stream<Arguments> invalidTemplates() {
        String box = "<rect id=\"caja-t\" x=\"1\" y=\"1\" width=\"500\" height=\"100\"/>";
        String field = "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\">Hola</text>";
        return Stream.of(
                Arguments.of("XML roto", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect></svg>", "no es un XML válido"),
                Arguments.of("raíz no svg", "<html/>", "La raíz debe ser <svg"),
                Arguments.of("sin viewBox", "<svg " + NS + ">" + box + field + "</svg>", "Falta el atributo viewBox"),
                Arguments.of("viewBox con 3 números", "<svg " + NS + " viewBox=\"0 0 1000\">" + box + field + "</svg>", "viewBox debe tener 4 números"),
                Arguments.of("viewBox demasiado pequeño", "<svg " + NS + " viewBox=\"0 0 50 50\">" + box + field + "</svg>", "entre 100 y 4000"),
                Arguments.of("script en el dibujo", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><script>alert(1)</script>" + box + field + "</svg>", "elementos o atributos no permitidos"),
                Arguments.of("texto dentro de g", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><g>" + field + "</g>" + box + "</svg>", "hijos directos de <svg>"),
                Arguments.of("tspan", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box
                        + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\"><tspan>Hola</tspan></text></svg>", "tiene elementos dentro"),
                Arguments.of("texto sin campo-", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><text x=\"1\" y=\"1\">Hola</text></svg>", "sin id «campo-nombre»"),
                Arguments.of("nombre no válido", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><text id=\"campo-Mal\" font-family=\"Fira Sans\" font-size=\"40\">x</text></svg>", "no es válido"),
                Arguments.of("campo sin caja", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + field + "</svg>", "no tiene su caja"),
                Arguments.of("caja sin campo", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "</svg>", "no tiene su campo"),
                Arguments.of("nombre repetido", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + field + field + "</svg>", "está repetido"),
                Arguments.of("caja repetida", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + box + field + "</svg>", "caja-t» está repetida"),
                Arguments.of("caja con unidades", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"1px\" y=\"1\" width=\"5\" height=\"5\"/>" + field + "</svg>", "sin unidades"),
                Arguments.of("caja de ancho cero", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"1\" y=\"1\" width=\"0\" height=\"5\"/>" + field + "</svg>", "mayores que 0"),
                Arguments.of("fuente no incluida", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box
                        + "<text id=\"campo-t\" font-family=\"Comic Sans\" font-size=\"40\">x</text></svg>", "no está incluida. Fuentes disponibles: Crimson Text, Fira Sans, Bebas Neue"),
                Arguments.of("font-family ausente", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-size=\"40\">x</text></svg>", "necesita font-family"),
                Arguments.of("font-size ausente", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-family=\"Fira Sans\">x</text></svg>", "necesita font-size"),
                Arguments.of("font-size fuera de rango", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"900\">x</text></svg>", "fuera de rango"),
                Arguments.of("fill no válido", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\" fill=\"red\">x</text></svg>", "fill no válido"),
                Arguments.of("text-anchor no válido", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\" text-anchor=\"left\">x</text></svg>", "text-anchor no válido"),
                Arguments.of("tamaño mínimo mayor que el máximo", "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + box + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\" data-tamano-min=\"50\">x</text></svg>", "data-tamano-min"),
                Arguments.of("font-style no válido", withAttr("font-style=\"oblique\""), "font-style no válido"),
                Arguments.of("font-weight no válido", withAttr("font-weight=\"heavy\""), "font-weight no válido"),
                Arguments.of("data-vertical no válido", withAttr("data-vertical=\"medio\""), "data-vertical no válido"),
                Arguments.of("data-fijo no válido", withAttr("data-fijo=\"si\""), "data-fijo no válido"),
                Arguments.of("data-lineas-max no entero", withAttr("data-lineas-max=\"2.5\""), "data-lineas-max entero"),
                Arguments.of("data-lineas-max fuera de rango", withAttr("data-lineas-max=\"31\""), "data-lineas-max"),
                Arguments.of("letter-spacing fuera de rango", withAttr("letter-spacing=\"50\""), "letter-spacing"),
                Arguments.of("fill-opacity fuera de rango", withAttr("fill-opacity=\"1.5\""), "fill-opacity"),
                Arguments.of("data-interlineado fuera de rango", withAttr("data-interlineado=\"3\""), "data-interlineado"),
                Arguments.of("caja fuera del lienzo", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"900\" y=\"1\" width=\"500\" height=\"100\"/>" + field + "</svg>", "no cabe dentro del lienzo"),
                Arguments.of("caja con número enorme", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"1\" y=\"1\" width=\"1" + "0".repeat(400) + "\" height=\"100\"/>" + field + "</svg>", "demasiado grande"),
                Arguments.of("font-size enorme", withAttr("data-x=\"1\"").replace("font-size=\"80\"", "font-size=\"1" + "0".repeat(400) + "\""), "font-size"),
                Arguments.of("viewBox con número enorme", "<svg " + NS + " viewBox=\"0 0 1" + "0".repeat(400) + " 1000\">" + box + field + "</svg>", "viewBox"),
                Arguments.of("origen del viewBox fuera de rango", "<svg " + NS + " viewBox=\"5000 0 1000 1000\">" + box + field + "</svg>", "entre -4000 y 4000"),
                Arguments.of("caja más baja que una línea", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"1\" y=\"1\" width=\"500\" height=\"20\"/>"
                        + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\">x</text></svg>", "caja demasiado pequeña"),
                Arguments.of("caja más estrecha que el tamaño mínimo", "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect id=\"caja-t\" x=\"1\" y=\"1\" width=\"10\" height=\"100\"/>"
                        + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"40\">x</text></svg>", "caja demasiado pequeña"),
                Arguments.of("más de 40 campos", manyFields(41), "más de 40 campos"),
                Arguments.of("DOCTYPE", "<!DOCTYPE svg [<!ENTITY x \"y\">]><svg " + NS + " viewBox=\"0 0 1000 1000\"/>", "no es un XML válido"),
                Arguments.of("entidad externa", "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg " + NS + " viewBox=\"0 0 1000 1000\"><text id=\"campo-t\">&x;</text></svg>", "no es un XML válido"));
    }

    private static String withAttr(String attr) {
        return template(attr, "x");
    }

    private static String manyFields(int count) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < count; i++) {
            body.append("<rect id=\"caja-c").append(i).append("\" x=\"1\" y=\"1\" width=\"500\" height=\"100\"/>")
                    .append("<text id=\"campo-c").append(i).append("\" font-family=\"Fira Sans\" font-size=\"40\">x</text>");
        }
        return "<svg " + NS + " viewBox=\"0 0 1000 1000\">" + body + "</svg>";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTemplates")
    void everyContractErrorIsReportedInSpanishAndNothingIsPainted(String name, String svg, String expected) {
        var check = renderer.check(svg);
        assertThat(check.valid()).isFalse();
        assertThat(check.errors()).anySatisfy(e -> assertThat(e).contains(expected));
        var render = renderer.render(svg, Map.of(), 1000, 1000);
        assertThat(render.ok()).isFalse();
        assertThat(render.image()).isNull();
        assertThat(render.errors()).isEqualTo(check.errors());
    }

    @Test
    void allTheErrorsAreReportedInOnePass() {
        String svg = "<svg " + NS + " viewBox=\"0 0 1000 1000\"><script>x</script><rect id=\"caja-a\" x=\"1\" y=\"1\" width=\"5\" height=\"5\"/>"
                + "<text id=\"campo-b\" font-family=\"Arial\" font-size=\"40\">x</text><text>suelto</text></svg>";
        assertThat(renderer.check(svg).errors()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void aRatioDifferentFromTheViewBoxIsAnError() {
        var result = renderer.render(template(""), Map.of(), 1080, 1350);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("proporción");
        assertThat(renderer.render(template(""), Map.of(), 500, 500).ok()).isTrue();
    }

    @Test
    void aValidTemplateHasNoErrorsAndExposesItsFields() {
        var check = renderer.check(template("data-fijo=\"true\"", "Hola"));
        assertThat(check.valid()).isTrue();
        assertThat(check.width()).isEqualTo(1000);
        assertThat(check.height()).isEqualTo(1000);
        assertThat(check.fields()).singleElement().satisfies(f -> {
            assertThat(f.name()).isEqualTo("t");
            assertThat(f.x()).isEqualTo(100);
            assertThat(f.width()).isEqualTo(600);
            assertThat(f.example()).isEqualTo("Hola");
            assertThat(f.fixed()).isTrue();
        });
    }

    // ---------------------------------------------------------------- límites, alineación con el dibujo, cajas

    @Test
    void aHugeTextIsCutTo2000CharactersWithAWarningAndRendersFast() {
        String svg = template("data-tamano-min=\"20\"");
        renderer.render(svg, Map.of("t", "calentar"), 1000, 1000);
        long start = System.nanoTime();
        var result = draw(svg, "palabra ".repeat(25_000));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(millis).isLessThan(2000);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("«t»").contains("cortado a 2000"));
        assertInsideBox(ink(result.image()));
    }

    @Test
    void aWordSplitByLettersIsAWarning() {
        var result = draw(template("data-tamano-min=\"20\""), "Supercalifragilisticoespialidosoextraordinariamente".repeat(2));
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("«t»").contains("partido por letras"));
        assertInsideBox(ink(result.image()));
    }

    @Test
    void manyUnknownKeysAreListedUpToTenAndCounted() {
        Map<String, String> texts = new java.util.TreeMap<>();
        for (int i = 0; i < 25; i++) texts.put(String.format("k%02d", i), "x");
        var result = renderer.render(template(""), texts, 1000, 1000);
        assertThat(result.warnings()).hasSize(11);
        assertThat(result.warnings().get(10)).contains("15 textos más");
    }

    @Test
    void withoutLinesMaxTheBoxHeightDecidesHowManyLines() {
        var check = renderer.check(template(""));
        assertThat(check.valid()).isTrue();
        String text = "uno\ndos\ntres\ncuatro\ncinco\nseis\nsiete\nocho";
        var result = draw(template("data-tamano-min=\"20\""), text);
        assertThat(result.warnings()).isEmpty();
        assertInsideBox(ink(result.image()));
    }

    private static final String ALIGN = "<svg " + NS + " viewBox=\"0 0 1080 1350\"><rect width=\"1080\" height=\"1350\" fill=\"#000000\"/>"
            + "<rect x=\"200\" y=\"700\" width=\"10\" height=\"100\" fill=\"#FF0000\"/>"
            + "<rect x=\"700\" y=\"100\" width=\"100\" height=\"10\" fill=\"#FF0000\"/>"
            + "<rect id=\"caja-t\" x=\"300\" y=\"600\" width=\"500\" height=\"300\" fill=\"none\"/>"
            + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-weight=\"bold\" font-size=\"120\" data-vertical=\"abajo\" fill=\"#FFFFFF\">HHH</text></svg>";

    /** Rectángulo de los píxeles rojos (las marcas del dibujo) con x en el rango dado. */
    private static Rectangle marker(BufferedImage image, int fromX, int toX) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = Math.max(0, fromX); x < Math.min(image.getWidth(), toX); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) > 128 && (rgb & 0xFF) < 64) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @ParameterizedTest
    @MethodSource("slightlyDifferentRatios")
    void theTextFollowsTheSameTransformAsTheDrawing(int w, int h) {
        Rectangle ref = ink(renderer.render(ALIGN, Map.of("t", "HHH"), 1080, 1350).image());
        assertThat(ref).isNotNull();
        var result = renderer.render(ALIGN, Map.of("t", "HHH"), w, h);
        assertThat(result.errors()).isEmpty();
        double s = Math.max(w / 1080.0, h / 1350.0);
        double ox = (w - 1080 * s) / 2;
        double oy = (h - 1350 * s) / 2;
        BufferedImage image = result.image();
        Rectangle vertical = marker(image, 0, 500);
        Rectangle horizontal = marker(image, 600, 2000);
        assertThat((double) vertical.x).isCloseTo(ox + s * 200, org.assertj.core.api.Assertions.within(1.0));
        assertThat((double) horizontal.y).isCloseTo(oy + s * 100, org.assertj.core.api.Assertions.within(1.0));
        Rectangle text = ink(image);
        // El texto, anclado a la caja, cae donde el dibujo: mismo desplazamiento y misma escala
        assertThat((double) text.x).isCloseTo(ox + s * ref.x, org.assertj.core.api.Assertions.within(1.5));
        assertThat((double) (text.y + text.height)).isCloseTo(oy + s * (ref.y + ref.height), org.assertj.core.api.Assertions.within(1.5));
    }

    static Stream<Arguments> slightlyDifferentRatios() {
        return Stream.of(Arguments.of(1090, 1350), Arguments.of(1080, 1363), Arguments.of(1080, 1350));
    }

    @Test
    void aVisibleFillInACajaIsNeverPainted() {
        String svg = "<svg " + NS + " viewBox=\"0 0 1000 1000\"><rect width=\"1000\" height=\"1000\" fill=\"#000000\"/>"
                + "<rect id=\"caja-t\" x=\"100\" y=\"200\" width=\"600\" height=\"300\" fill=\"#FF0000\" stroke=\"#00FF00\"/>"
                + "<text id=\"campo-t\" font-family=\"Fira Sans\" font-size=\"80\">x</text></svg>";
        var result = renderer.render(svg, Map.of(), 1000, 1000);
        assertThat(result.ok()).isTrue();
        assertThat(result.image().getRGB(400, 350) & 0xFFFFFF).isEqualTo(0);
        assertThat(result.image().getRGB(100, 350) & 0xFFFFFF).isEqualTo(0);
    }

    @Test
    void paintingAbove1080x1920IsAnError() {
        var result = renderer.render(template(""), Map.of(), 2000, 2000);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("1080×1920");
    }

    // ---------------------------------------------------------------- plantillas de prueba y vistas previas

    private static String resource(String name) throws IOException {
        try (InputStream in = SvgTemplateRendererTest.class.getResourceAsStream("/templates/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theTestTemplatesAreValid() throws IOException {
        for (String name : List.of("quote-card.svg", "stat-card.svg", "story.svg")) {
            var check = renderer.check(resource(name));
            assertThat(check.errors()).as(name).isEmpty();
            assertThat(check.fields()).as(name).isNotEmpty();
        }
    }

    @Test
    void writesThePreviewPngsOfTheTestTemplates() throws IOException {
        Path dir = Path.of("target", "plantillas-previa");
        Files.createDirectories(dir);
        record Case(String file, int w, int h, Map<String, String> texts) {}
        List<Case> cases = List.of(
                new Case("quote-card.svg", 1080, 1350, Map.of("cita", "Nadie sabe lo que tiene\nhasta que lo ve perdido.\n¿Y entonces qué? ¡Vuelve a empezar!", "autor", "RAÚL VILLALBA")),
                new Case("stat-card.svg", 1080, 1350, Map.of("cifra", "42%", "titulo", "de los oyentes repite el disco entero", "detalle", "Datos de ejemplo & prueba · https://ejemplo.es")),
                new Case("story.svg", 1080, 1920, Map.of("titular", "Litoques ya suena", "cuerpo", "Escúchalo hoy, ¿te vienes?", "enlace", "https://ejemplo.es/litoques")));
        for (Case c : cases) {
            var preview = renderer.preview(resource(c.file()), c.w(), c.h());
            assertThat(preview.ok()).as(c.file()).isTrue();
            ImageIO.write(preview.image(), "png", dir.resolve(c.file().replace(".svg", "-ejemplo.png")).toFile());
            var real = renderer.render(resource(c.file()), c.texts(), c.w(), c.h());
            assertThat(real.ok()).as(c.file()).isTrue();
            ImageIO.write(real.image(), "png", dir.resolve(c.file().replace(".svg", "-real.png")).toFile());
        }
        assertThat(Files.list(dir).count()).isGreaterThanOrEqualTo(6);
    }
}
