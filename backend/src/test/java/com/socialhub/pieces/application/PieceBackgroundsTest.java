package com.socialhub.pieces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.socialhub.pieces.adapter.out.svg.BatikSvgRasterizer;
import com.socialhub.pieces.domain.BrandLook;
import com.socialhub.pieces.domain.PieceFiles;
import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.tools.Ffmpeg;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El orden de elección del fondo de una pieza sin FFmpeg (carrusel e historia): imagen propia, fondo SVG de Claude, portada de
 * la marca y fondo de SocialHub. Y que un spec ya guardado, con «img», sigue valiendo.
 */
class PieceBackgroundsTest {

    private static final String SVG_RED = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1080 1350\">"
            + "<rect width=\"1080\" height=\"1350\" fill=\"#ff0000\"/></svg>";

    @TempDir Path drive;
    private final PieceGenerator generator = new PieceGenerator(mock(Ffmpeg.class), new BatikSvgRasterizer());
    private PieceFiles files;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void folders() throws Exception {
        Files.createDirectories(drive.resolve("material"));
        files = new PieceFiles(List.of(drive.toString()), drive);
    }

    private Path solid(String name, Color color) throws Exception {
        BufferedImage image = new BufferedImage(1080, 1350, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 1080, 1350);
        g.dispose();
        Path file = drive.resolve("material").resolve(name);
        ImageIO.write(image, name.endsWith("png") ? "png" : "jpg", file.toFile());
        return file;
    }

    private static PromoSpec.Piece story(String img, String svg) {
        return new PromoSpec.Piece("historia", "mi_historia", null, null, null, null, null, null, img, "Con púa", null, null, null,
                null, null, null, null, null, null, null, null, svg);
    }

    private static PromoSpec.Piece carousel(PromoSpec.Slide... slides) {
        return new PromoSpec.Piece("carrusel", "mi_carrusel", null, null, null, null, List.of(slides),
                new PromoSpec.Closing(null, List.of("Gracias", "Hasta pronto")), null, "Con púa", null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private BufferedImage run(PromoSpec.Piece piece, BrandLook look) throws Exception {
        var batch = generator.generate(new PromoSpec("salida", null, null, List.of(piece)), files, look, log::add, p -> {});
        return ImageIO.read(batch.pieces().getFirst().files().getFirst().toFile());
    }

    private static boolean reddish(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > c.getGreen() + 40 && c.getRed() > c.getBlue() + 40;
    }

    private static boolean greenish(int rgb) {
        Color c = new Color(rgb);
        return c.getGreen() > c.getRed() + 40 && c.getGreen() > c.getBlue() + 40;
    }

    private static boolean blueish(int rgb) {
        Color c = new Color(rgb);
        return c.getBlue() > c.getRed() + 40 && c.getBlue() > c.getGreen() + 40;
    }

    @Test
    void ownImageBeatsEverythingElse() throws Exception {
        solid("propia.png", new Color(0, 255, 0));
        solid("portada.png", new Color(0, 0, 255));
        var image = run(story("material/propia.png", SVG_RED), new BrandLook("#FF8000", drive.resolve("material/portada.png")));
        assertThat(greenish(image.getRGB(20, 20))).isTrue();
        assertThat(log).noneMatch(l -> l.contains("sin imagen"));
    }

    @Test
    void claudesSvgBeatsTheBrandCoverAndTheGeneratedBackground() throws Exception {
        solid("portada.png", new Color(0, 0, 255));
        var image = run(story(null, SVG_RED), new BrandLook("#FF8000", drive.resolve("material/portada.png")));
        assertThat(reddish(image.getRGB(20, 20))).isTrue();
        assertThat(log).anyMatch(l -> l.contains("fondo SVG de Claude"));
    }

    @Test
    void anUnsafeSvgFallsBackToTheCoverWithoutError() throws Exception {
        solid("portada.png", new Color(0, 0, 255));
        String evil = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        var image = run(story(null, evil), new BrandLook("#FF8000", drive.resolve("material/portada.png")));
        assertThat(blueish(image.getRGB(20, 20))).isTrue();
        assertThat(log).anyMatch(l -> l.contains("no es válido")).anyMatch(l -> l.contains("portada de la marca"));
    }

    @Test
    void theBrandCoverBeatsTheGeneratedBackground() throws Exception {
        solid("portada.png", new Color(0, 0, 255));
        var image = run(story(null, null), new BrandLook("#FF8000", drive.resolve("material/portada.png")));
        assertThat(blueish(image.getRGB(20, 20))).isTrue();
    }

    @Test
    void withNothingTheBrandColoredBackgroundIsUsedAndNoMissingImgError() throws Exception {
        var image = run(story(null, null), new BrandLook("#D23A2E", null));
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1920);
        assertThat(log).anyMatch(l -> l.contains("fondo con el color de la marca"));
        // Determinista: el mismo spec da la misma imagen
        var again = run(story(null, null), new BrandLook("#D23A2E", null));
        assertThat(again.getRGB(300, 300)).isEqualTo(image.getRGB(300, 300));
    }

    @Test
    void aBrandWithoutColorGeneratesNoBackgroundNeitherClaudesSvgNorSocialHubs() {
        // Con el SVG de Claude o sin él: sin color no se dibuja nada y se dice por qué
        for (var piece : List.of(story(null, null), story(null, SVG_RED))) {
            assertThatThrownBy(() -> run(piece, new BrandLook(null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("la marca no tiene color");
        }
        assertThat(log).noneMatch(l -> l.contains("fondo SVG de Claude")).noneMatch(l -> l.contains("fondo con el color de la marca"));
    }

    @Test
    void aBrandWithoutColorStillUsesItsOwnImageOrItsCover() throws Exception {
        solid("propia.png", new Color(0, 255, 0));
        solid("portada.png", new Color(0, 0, 255));
        assertThat(greenish(run(story("material/propia.png", SVG_RED), new BrandLook(null, null)).getRGB(20, 20))).isTrue();
        var withCover = run(story(null, SVG_RED), new BrandLook("", drive.resolve("material/portada.png")));
        assertThat(blueish(withCover.getRGB(20, 20))).isTrue();
    }

    @Test
    void aCoverOutsideThePromotionFoldersIsIgnoredWithoutFailing() throws Exception {
        Path outside = Files.createTempDirectory("fuera");
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", outside.resolve("portada.png").toFile());
        var image = run(story(null, null), new BrandLook("#FF8000", outside.resolve("portada.png")));
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(log).anyMatch(l -> l.contains("no se pudo usar la portada"));
    }

    @Test
    void aCarouselWithoutImagesIsAcceptedAndEachSlideGetsItsOwnBackground() throws Exception {
        var piece = carousel(new PromoSpec.Slide(null, "Primer verso", "Con púa", null, null),
                new PromoSpec.Slide(null, "Segundo verso", null, null, null),
                new PromoSpec.Slide(null, "Tercer verso", null, null, null));
        var batch = generator.generate(new PromoSpec("salida", null, null, List.of(piece)), files, new BrandLook("#FF8000", null),
                log::add, p -> {});
        assertThat(batch.pieces().getFirst().files()).hasSize(4); // 3 slides y el cierre
        BufferedImage second = ImageIO.read(batch.pieces().getFirst().files().get(1).toFile());
        BufferedImage third = ImageIO.read(batch.pieces().getFirst().files().get(2).toFile());
        boolean differs = false;
        for (int y = 0; y < 900 && !differs; y += 10) for (int x = 0; x < 1080; x += 10) if (second.getRGB(x, y) != third.getRGB(x, y)) { differs = true; break; }
        assertThat(differs).isTrue();
        assertThat(second.getHeight()).isEqualTo(1350);
    }

    @Test
    void validationNoLongerAsksForImgButStillRequiresSomethingToShow() {
        assertThatCode(() -> generator.validate(new PromoSpec(null, null, null, List.of(story(null, null))), files)).doesNotThrowAnyException();
        var empty = carousel(new PromoSpec.Slide(null, null, null, null, null));
        assertThatThrownBy(() -> generator.validate(new PromoSpec(null, null, null, List.of(empty)), files))
                .hasMessageContaining("no tiene texto ni título");
        var noTitle = new PromoSpec.Piece("historia", "x", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> generator.validate(new PromoSpec(null, null, null, List.of(noTitle)), files))
                .hasMessageContaining("falta «titulo»");
        // Una imagen que se indica y no existe sigue siendo un error
        assertThatThrownBy(() -> generator.validate(new PromoSpec(null, null, null, List.of(story("material/no.png", null))), files))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
