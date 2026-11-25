package com.socialhub.pieces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.socialhub.pieces.ApprovedTemplates;
import com.socialhub.pieces.adapter.out.files.SpecFileLoader;
import com.socialhub.pieces.adapter.out.svg.BatikSvgRasterizer;
import com.socialhub.pieces.adapter.out.svg.SvgTemplateRenderer;
import com.socialhub.pieces.domain.BrandLook;
import com.socialhub.pieces.domain.PieceBatch;
import com.socialhub.pieces.domain.PieceFiles;
import com.socialhub.pieces.domain.PieceResult;
import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.tools.Ffmpeg;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fase 13, bloque 3: plantillas del kit aprobado en las piezas (cita, cierre del carrusel, historia), tamaño según la red,
 * página y archivo de cada diapositiva. Y que sin kit aprobado todo sale como antes.
 */
class PieceTemplatesTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";
    private static final BrandLook LOOK = new BrandLook("#7A1F1F", null);

    /** Cita 4:5 (1080×1350): fondo negro y dos campos blancos. */
    private static final String QUOTE_4X5 = "<svg " + NS + " viewBox=\"0 0 1080 1350\"><rect width=\"1080\" height=\"1350\" fill=\"#000000\"/>"
            + "<rect id=\"caja-cita\" x=\"100\" y=\"300\" width=\"880\" height=\"600\" fill=\"none\"/>"
            + "<text id=\"campo-cita\" font-family=\"Crimson Text\" font-size=\"90\" fill=\"#FFFFFF\" text-anchor=\"middle\" data-vertical=\"centro\">Verso</text>"
            + "<rect id=\"caja-autor\" x=\"100\" y=\"1000\" width=\"880\" height=\"100\" fill=\"none\"/>"
            + "<text id=\"campo-autor\" font-family=\"Fira Sans\" font-size=\"40\" fill=\"#FFFFFF\" text-anchor=\"middle\">AUTOR</text></svg>";

    /** La misma cita en 9:16 (1080×1920). */
    private static final String QUOTE_9X16 = QUOTE_4X5.replace("0 0 1080 1350", "0 0 1080 1920").replace("height=\"1350\"", "height=\"1920\"");

    /** Cierre 4:5: banda negra abajo, zona de la ilustración libre arriba (90,60,900,800). */
    private static String closing(int height) {
        return "<svg " + NS + " viewBox=\"0 0 1080 " + height + "\"><rect x=\"0\" y=\"" + (height - 350) + "\" width=\"1080\" height=\"350\" fill=\"#000000\"/>"
                + "<rect id=\"caja-titulo\" x=\"60\" y=\"" + (height - 330) + "\" width=\"960\" height=\"120\" fill=\"none\"/>"
                + "<text id=\"campo-titulo\" font-family=\"Fira Sans\" font-size=\"60\" fill=\"#FFFFFF\" data-tamano-min=\"30\">T</text>"
                + "<rect id=\"caja-texto\" x=\"60\" y=\"" + (height - 190) + "\" width=\"960\" height=\"150\" fill=\"none\"/>"
                + "<text id=\"campo-texto\" font-family=\"Fira Sans\" font-size=\"40\" fill=\"#FFFFFF\" data-tamano-min=\"20\">t</text></svg>";
    }

    private static ApprovedTemplates.Template quote(String id, String svg, String ratio, int h) {
        return new ApprovedTemplates.Template(id, "cita", "Cita", svg, null, List.of("cita"), List.of("autor"), List.of(), ratio, 1080, h);
    }

    private static ApprovedTemplates.Template closing(String id, String ratio, int h) {
        return new ApprovedTemplates.Template(id, "cierre", "Cierre", closing(h), new ApprovedTemplates.Zone(90, 60, 900, 800),
                List.of("titulo", "texto"), List.of(), List.of(), ratio, 1080, h);
    }

    @TempDir Path drive;
    private PieceFiles files;
    private final List<String> log = new ArrayList<>();
    private List<ApprovedTemplates.Template> kit = List.of();
    private PieceGenerator generator;

    @BeforeEach
    void prepare() throws Exception {
        Files.createDirectories(drive.resolve("material"));
        files = new PieceFiles(List.of(drive.toString()), drive);
        var rasterizer = new BatikSvgRasterizer();
        generator = new PieceGenerator(mock(Ffmpeg.class), rasterizer, new SvgTemplateRenderer(rasterizer), brand -> brand.equals("delito") ? kit : List.of());
    }

    private void solid(String name, Color color) throws Exception {
        BufferedImage image = new BufferedImage(1080, 1350, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 1080, 1350);
        g.dispose();
        ImageIO.write(image, "png", drive.resolve("material").resolve(name).toFile());
    }

    private static PromoSpec.Piece piece(String tipo, String name, String publication, PromoSpec.Slide[] slides, PromoSpec.Closing closing,
                                         String img, String template, Map<String, String> fields, Integer page, PromoSpec.Sound sound) {
        return new PromoSpec.Piece(tipo, name, null, publication, null, null, slides == null ? null : List.of(slides), closing, img,
                "Con púa", null, null, null, null, null, null, null, null, null, null, null, null, template, fields, page, sound);
    }

    /** Una historia sin «titulo» propio: su texto, si lo hay, va en «campos». */
    private static PromoSpec.Piece untitledStory(String name, Map<String, String> fields) {
        return new PromoSpec.Piece("historia", name, null, null, null, null, null, null, "material/page 12.png", null, null, null, null,
                null, null, null, null, null, null, null, null, null, "historia-9x16", fields, null, null);
    }

    private PieceBatch run(GenerationSetup setup, PromoSpec.Piece... pieces) throws Exception {
        return generator.generate(new PromoSpec("salida", null, null, List.of(pieces)), files, LOOK, log::add, p -> {}, setup);
    }

    private PieceBatch run(PromoSpec.Piece... pieces) throws Exception {
        return run(new GenerationSetup("delito", id -> false), pieces);
    }

    private static BufferedImage read(Path file) throws Exception {
        return ImageIO.read(file.toFile());
    }

    private static boolean dark(int rgb) { return new Color(rgb).getRed() < 40 && new Color(rgb).getGreen() < 40; }

    private static boolean greenish(int rgb) {
        Color c = new Color(rgb);
        return c.getGreen() > c.getRed() + 40 && c.getGreen() > c.getBlue() + 40;
    }

    /** Hay píxeles claros (tinta del texto) en esa franja de un fondo negro. */
    private static boolean hasInk(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (new Color(image.getRGB(x, y)).getRed() > 200) return true;
        return false;
    }

    private static PromoSpec.Slide[] slides() {
        return new PromoSpec.Slide[] {new PromoSpec.Slide("material/page 12.png", "Un verso", "Con púa", null, null, 11)};
    }

    // ---------------------------------------------------------------- cita

    @Test
    void aQuoteWithATemplateOfElDelitoIsDrawnOverTheBrandBackground() throws Exception {
        kit = List.of(quote("cita-4x5", QUOTE_4X5, "4:5", 1350));
        var batch = run(piece("cita", "cita_uno", null, null, null, null, "cita-4x5",
                Map.of("cita", "Un verso del libro\nen dos líneas", "autor", "LITO"), 11, null));
        var image = read(batch.pieces().getFirst().files().getFirst());
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1350);
        assertThat(dark(image.getRGB(10, 10))).isTrue();
        assertThat(hasInk(image, 100, 300, 980, 900)).as("el verso está dentro de su caja").isTrue();
        assertThat(hasInk(image, 100, 1000, 980, 1100)).as("el autor está dentro de su caja").isTrue();
        assertThat(batch.pieces().getFirst().type()).isEqualTo("cita");
    }

    @Test
    void aQuoteWithoutAnApprovedKitFailsWithAClearMessage() {
        kit = List.of();
        assertThatThrownBy(() -> run(piece("cita", "cita_uno", null, null, null, null, "cita-4x5", Map.of("cita", "x"), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pieza «cita_uno»").hasMessageContaining("no tiene un kit de marca aprobado");
    }

    @Test
    void aQuoteNeedsATemplateInTheSpecItself() {
        assertThatThrownBy(() -> generator.validate(new PromoSpec("salida", null, null,
                List.of(piece("cita", "cita_uno", null, null, null, null, null, Map.of("cita", "x"), null, null))), files))
                .hasMessageContaining("una cita necesita «plantilla»");
    }

    // ---------------------------------------------------------------- errores de plantilla

    @Test
    void anUnknownTemplateIsAClearErrorInSpanishAndNothingIsGenerated() throws Exception {
        kit = List.of(quote("cita-4x5", QUOTE_4X5, "4:5", 1350), closing("cierre-4x5", "4:5", 1350));
        solid("page 12.png", new Color(0, 200, 0));
        var closingWithBadTemplate = new PromoSpec.Closing(null, List.of(), "no-existe", Map.of("titulo", "a", "texto", "b"), null);
        assertThatThrownBy(() -> run(piece("carrusel", "mi_carrusel", null, slides(), closingWithBadTemplate, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("la plantilla «no-existe» no existe en el kit aprobado de la marca")
                .hasMessageContaining("Plantillas de «cierre»: cierre-4x5");
        assertThat(drive.resolve("salida")).doesNotExist();
    }

    @Test
    void aTemplateOfAnotherPieceMissingFieldsOrWrongRatioIsRejected() throws Exception {
        kit = List.of(quote("cita-4x5", QUOTE_4X5, "4:5", 1350), closing("cierre-4x5", "4:5", 1350), closing("cierre-9x16", "9:16", 1920));
        solid("page 12.png", new Color(0, 200, 0));
        var wrongPiece = new PromoSpec.Closing(null, List.of(), "cita-4x5", Map.of("cita", "x"), null);
        assertThatThrownBy(() -> run(piece("carrusel", "c1", null, slides(), wrongPiece, null, null, null, null, null)))
                .hasMessageContaining("es de la pieza «cita» y aquí hace falta una de «cierre»");
        var missing = new PromoSpec.Closing(null, List.of(), "cierre-4x5", Map.of("titulo", "solo título"), null);
        assertThatThrownBy(() -> run(piece("carrusel", "c2", null, slides(), missing, null, null, null, null, null)))
                .hasMessageContaining("faltan campos obligatorios").hasMessageContaining("texto");
        var tall = new PromoSpec.Closing(null, List.of(), "cierre-9x16", Map.of("titulo", "a", "texto", "b"), null);
        assertThatThrownBy(() -> run(piece("carrusel", "c3", null, slides(), tall, null, null, null, null, null)))
                .hasMessageContaining("es 9:16 y esta pieza sale en 1080×1350 (4:5)").hasMessageContaining("cierre-4x5");
    }

    @Test
    void aQuoteForTikTokNeedsANineBySixteenTemplate() {
        kit = List.of(quote("cita-4x5", QUOTE_4X5, "4:5", 1350), quote("cita-9x16", QUOTE_9X16, "9:16", 1920));
        var setup = new GenerationSetup("delito", id -> id.equals("tt"));
        assertThatThrownBy(() -> run(setup, piece("cita", "cita_tt", "tt", null, null, null, "cita-4x5", Map.of("cita", "x"), null, null)))
                .hasMessageContaining("es 4:5 y esta pieza sale en 1080×1920 (9:16)").hasMessageContaining("cita-9x16");
    }

    @Test
    void aTemplateOnTheWrongKindOfPieceIsRejectedBySpecValidation() {
        var carouselWithTemplate = piece("carrusel", "c", null, slides(), null, null, "cierre-4x5", null, null, null);
        assertThatThrownBy(() -> generator.validate(new PromoSpec("salida", null, null, List.of(carouselWithTemplate)), files))
                .hasMessageContaining("«cierre.plantilla»");
        var reel = piece("reel", "r", null, null, null, null, "cierre-4x5", null, null, null);
        assertThatThrownBy(() -> generator.validate(new PromoSpec("salida", null, null, List.of(reel)), files))
                .hasMessageContaining("no admite plantilla");
    }

    @Test
    void reservedTypesAreInTheContractButNotActive() {
        for (String type : List.of("infografia", "meme")) {
            var reserved = piece(type, "x", null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> generator.validate(new PromoSpec("salida", null, null, List.of(reserved)), files))
                    .hasMessageContaining("todavía no está activo");
        }
    }

    // ---------------------------------------------------------------- cierre con ilustración debajo

    @Test
    void theClosingTemplateGoesOverTheIllustrationInItsZone() throws Exception {
        kit = List.of(closing("cierre-4x5", "4:5", 1350));
        solid("page 12.png", new Color(0, 200, 0));
        var closing = new PromoSpec.Closing("material/page 12.png", List.of(), "cierre-4x5", Map.of("titulo", "El DeLito", "texto", "Enlace en la bio"), 12);
        var batch = run(piece("carrusel", "mi_carrusel", null, slides(), closing, null, null, null, null, null));
        var files = batch.pieces().getFirst().files();
        assertThat(files).hasSize(2);
        var image = read(files.get(1));
        assertThat(image.getHeight()).isEqualTo(1350);
        assertThat(greenish(image.getRGB(500, 400))).as("la ilustración se ve en su zona").isTrue();
        assertThat(dark(image.getRGB(500, 1300))).as("la plantilla va encima, abajo").isTrue();
        assertThat(hasInk(image, 60, 1020, 1020, 1140)).as("el título está sobre la plantilla").isTrue();
        assertThat(new Color(image.getRGB(10, 10)).getRed()).as("fuera de la zona y del dibujo, el lienzo de siempre").isGreaterThan(200);
        assertThat(log).anyMatch(l -> l.contains("con la plantilla «cierre-4x5»"));
    }

    // ---------------------------------------------------------------- sin kit, todo igual

    private static GenerationSetup setup() { return new GenerationSetup("delito", id -> false); }

    @Test
    void aClosingWithATemplateDoesNotNeedLinesInTheSpecJson() throws Exception {
        kit = List.of(closing("cierre-4x5", "4:5", 1350));
        solid("page 12.png", new Color(0, 200, 0));
        String json = """
                {"out":"salida","piezas":[{"tipo":"carrusel","nombre":"c","slides":[{"texto":"Verso"}],
                 "cierre":{"img":"material/page 12.png","plantilla":"cierre-4x5","campos":{"titulo":"El DeLito","texto":"Enlace"}}}]}""";
        Files.writeString(drive.resolve("sin_lineas.json"), json);
        var spec = new SpecFileLoader(JsonMapper.builder().build()).read(drive.resolve("sin_lineas.json").toString(), List.of(drive.toString())).spec();
        assertThat(spec.piezas().getFirst().cierre().lineas()).isEmpty();
        var made = generator.generate(spec, files, LOOK, log::add, p -> {}, setup()).pieces().getFirst();
        assertThat(read(made.files().get(1)).getHeight()).isEqualTo(1350);
    }

    @Test
    void aStoryOrClosingWithATemplateAndNoKitWithoutMinimumTextsIsRejectedBeforeGeneratingAnything() {
        kit = List.of();
        var story = untitledStory("h", Map.of());
        assertThatThrownBy(() -> generator.checkTemplates(new PromoSpec("salida", null, null, List.of(story)), setup(), log::add))
                .hasMessageContaining("la historia pide plantilla").hasMessageContaining("«titulo»");
        var closing = new PromoSpec.Closing("material/page 12.png", null, "cierre-4x5", Map.of(), null);
        var carousel = piece("carrusel", "c", null, slides(), closing, null, null, null, null, null);
        assertThatThrownBy(() -> generator.checkTemplates(new PromoSpec("salida", null, null, List.of(carousel)), setup(), log::add))
                .hasMessageContaining("el cierre pide plantilla").hasMessageContaining("«lineas»");
        // Con los textos mínimos, pasa
        var okStory = untitledStory("h2", Map.of("titulo", "Pregunta"));
        generator.checkTemplates(new PromoSpec("salida", null, null, List.of(okStory)), setup(), log::add);
    }

    @Test
    void withoutAnApprovedKitTheTemplateIsIgnoredAndThePieceIsAsBefore() throws Exception {
        kit = List.of();
        solid("page 12.png", new Color(0, 200, 0));
        var withTemplate = new PromoSpec.Closing("material/page 12.png", List.of("Gracias", "Hasta pronto"), "cierre-4x5",
                Map.of("titulo", "a", "texto", "b"), null);
        var asBefore = new PromoSpec.Closing("material/page 12.png", List.of("Gracias", "Hasta pronto"));
        var one = run(piece("carrusel", "con_plantilla", null, slides(), withTemplate, null, null, null, null, null));
        var two = run(piece("carrusel", "como_antes", null, slides(), asBefore, null, null, null, null, null));
        BufferedImage a = read(one.pieces().getFirst().files().get(1)), b = read(two.pieces().getFirst().files().get(1));
        assertThat(a.getWidth()).isEqualTo(b.getWidth());
        assertThat(a.getHeight()).isEqualTo(1350);
        for (int y = 0; y < a.getHeight(); y += 7) for (int x = 0; x < a.getWidth(); x += 7) assertThat(a.getRGB(x, y)).isEqualTo(b.getRGB(x, y));
        assertThat(log).anyMatch(l -> l.contains("la marca no tiene un kit aprobado; se ignora la plantilla «cierre-4x5»"));
    }

    @Test
    void withoutAKitAStoryAndAClosingThatAskedForATemplateUseTheirFieldTexts() throws Exception {
        kit = List.of();
        solid("page 12.png", new Color(0, 200, 0));
        // La historia no trae «titulo» propio: sin kit, se usa el de «campos» y sale en 1080×1920 como siempre
        var story = untitledStory("h", Map.of("titulo", "Pregunta", "texto", "Frase", "cta", "Enlace"));
        var made = run(story).pieces().getFirst();
        assertThat(read(made.files().getFirst()).getHeight()).isEqualTo(1920);
        var closing = new PromoSpec.Closing("material/page 12.png", List.of(), "cierre-4x5", Map.of("titulo", "El DeLito", "texto", "Enlace"), null);
        var carousel = run(piece("carrusel", "c", null, slides(), closing, null, null, null, null, null)).pieces().getFirst();
        assertThat(read(carousel.files().get(1)).getHeight()).isEqualTo(1350);
        // Y sin título en ningún sitio, error claro
        assertThatThrownBy(() -> run(untitledStory("h2", Map.of())))
                .hasMessageContaining("la historia pide plantilla");
    }

    @Test
    void withAnApprovedKitButNoTemplateThePieceIsAsBefore() throws Exception {
        kit = List.of(closing("cierre-4x5", "4:5", 1350));
        solid("page 12.png", new Color(0, 200, 0));
        var batch = run(piece("carrusel", "sin_plantilla", null, slides(), new PromoSpec.Closing("material/page 12.png", List.of("Gracias")),
                null, null, null, null, null));
        var image = read(batch.pieces().getFirst().files().get(1));
        assertThat(image.getHeight()).isEqualTo(1350);
        assertThat(log).noneMatch(l -> l.contains("del kit de marca"));
    }

    // ---------------------------------------------------------------- tamaño según la red

    @Test
    void aTikTokCarouselIsNineBySixteenAndAnInstagramOneStaysFourByFive() throws Exception {
        solid("page 12.png", new Color(0, 200, 0));
        var closing = new PromoSpec.Closing("material/page 12.png", List.of("Gracias"));
        var tiktok = new GenerationSetup("delito", id -> id.equals("tt"));
        var batch = run(tiktok,
                piece("carrusel", "carrusel_tt", "tt", slides(), closing, null, null, null, null, null),
                piece("carrusel", "carrusel_ig", "ig", slides(), closing, null, null, null, null, null));
        for (Path file : batch.pieces().get(0).files()) {
            var image = read(file);
            assertThat(image.getWidth()).isEqualTo(1080);
            assertThat(image.getHeight()).as(file.getFileName().toString()).isEqualTo(1920);
        }
        for (Path file : batch.pieces().get(1).files()) assertThat(read(file).getHeight()).isEqualTo(1350);
        // La ilustración de la primera diapositiva sigue arriba en el vertical
        assertThat(greenish(read(batch.pieces().get(0).files().get(0)).getRGB(500, 300))).isTrue();
    }

    @Test
    void aTikTokClosingTemplateNineBySixteenFillsTheWholeCanvas() throws Exception {
        kit = List.of(closing("cierre-9x16", "9:16", 1920));
        solid("page 12.png", new Color(0, 200, 0));
        var closing = new PromoSpec.Closing("material/page 12.png", List.of(), "cierre-9x16", Map.of("titulo", "El DeLito", "texto", "Enlace"), null);
        var batch = run(new GenerationSetup("delito", id -> id.equals("tt")), piece("carrusel", "carrusel_tt", "tt", slides(), closing, null, null, null, null, null));
        var image = read(batch.pieces().getFirst().files().get(1));
        assertThat(image.getHeight()).isEqualTo(1920);
        assertThat(dark(image.getRGB(500, 1850))).isTrue();
    }

    // ---------------------------------------------------------------- página y archivo por diapositiva

    @Test
    void everyGeneratedFileKeepsItsPageAndImageName() throws Exception {
        solid("page 12.png", new Color(0, 200, 0));
        var closing = new PromoSpec.Closing("material/page 12.png", List.of("Gracias"), null, null, 12);
        var batch = run(piece("carrusel", "mi_carrusel", null, slides(), closing, null, null, null, null, null));
        PieceResult result = batch.pieces().getFirst();
        var slide = result.sourceOf(result.files().get(0));
        var close = result.sourceOf(result.files().get(1));
        assertThat(slide).isNotNull();
        assertThat(slide.page()).isEqualTo(11);
        assertThat(slide.imageName()).isEqualTo("page 12.png");
        assertThat(close.page()).isEqualTo(12);
        assertThat(close.imageName()).isEqualTo("page 12.png");
    }

    // ---------------------------------------------------------------- sonido

    @Test
    void theSoundOfASpecIsCheckedAndCarriedInTheResult() throws Exception {
        solid("page 12.png", new Color(0, 200, 0));
        var sound = new PromoSpec.Sound("network", "Púa");
        var batch = run(piece("historia", "h", "ig", null, null, "material/page 12.png", null, null, null, sound));
        assertThat(batch.pieces().getFirst().sound()).isEqualTo(sound);
        for (var bad : List.of(new PromoSpec.Sound("loud", "x"), new PromoSpec.Sound("litoques_track", null),
                new PromoSpec.Sound("network", " "))) {
            assertThatThrownBy(() -> generator.validate(new PromoSpec("salida", null, null,
                    List.of(piece("historia", "h", "ig", null, null, "material/page 12.png", null, null, null, bad))), files))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sonido");
        }
        assertThat(new PromoSpec.Sound("none", null)).isNotNull();
        generator.validate(new PromoSpec("salida", null, null,
                List.of(piece("historia", "h", "ig", null, null, "material/page 12.png", null, null, null, new PromoSpec.Sound("none", null)))), files);
    }

    // ---------------------------------------------------------------- el spec antiguo sigue valiendo

    @Test
    void anOldSpecAndANewSpecBothLoadFromJson() throws Exception {
        Files.writeString(drive.resolve("old.json"), """
                {"piezas": [{"tipo": "historia", "nombre": "h", "img": "material/page 12.png", "titulo": "t"}]}
                """);
        Files.writeString(drive.resolve("new.json"), """
                {"piezas": [
                  {"tipo": "cita", "nombre": "c", "plantilla": "cita-4x5", "campos": {"cita": "v", "autor": "L"}, "pagina": 3,
                   "sonido": {"modo": "litoques_track", "tema": "Púa"}, "publicacion": "p1"},
                  {"tipo": "carrusel", "nombre": "k", "slides": [{"texto": "a", "pagina": 5, "img": "material/page 12.png"}],
                   "cierre": {"lineas": [], "plantilla": "cierre-4x5", "campos": {"titulo": "t", "texto": "x"}, "pagina": 6}}]}
                """);
        var loader = new SpecFileLoader(JsonMapper.builder().build());
        var old = loader.read(drive.resolve("old.json").toString(), List.of(drive.toString())).spec();
        assertThat(old.piezas().getFirst().plantilla()).isNull();
        assertThat(old.piezas().getFirst().sonido()).isNull();
        var spec = loader.read(drive.resolve("new.json").toString(), List.of(drive.toString())).spec();
        assertThat(spec.piezas().get(0).campos()).containsEntry("cita", "v");
        assertThat(spec.piezas().get(0).sonido()).isEqualTo(new PromoSpec.Sound("litoques_track", "Púa"));
        assertThat(spec.piezas().get(0).pagina()).isEqualTo(3);
        assertThat(spec.piezas().get(1).slides().getFirst().pagina()).isEqualTo(5);
        assertThat(spec.piezas().get(1).cierre().plantilla()).isEqualTo("cierre-4x5");
        assertThat(spec.piezas().get(1).cierre().pagina()).isEqualTo(6);
    }
}
