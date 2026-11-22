package com.socialhub.pieces.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FallbackBackgroundTest {

    private static boolean same(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y += 7) for (int x = 0; x < a.getWidth(); x += 7) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    @Test
    void theSameInputGivesTheSameBackgroundAndOtherTitlesGiveOtherOnes() {
        var a = FallbackBackground.render(1080, 1350, "#FF8000", "Con púa", "Con púa", 0.5);
        var again = FallbackBackground.render(1080, 1350, "#FF8000", "Con púa", "Con púa", 0.5);
        var accents = FallbackBackground.render(1080, 1350, "#FF8000", "CON PUA", "Con púa", 0.5);
        var other = FallbackBackground.render(1080, 1350, "#FF8000", "Pregunta grande", "Pregunta grande", 0.5);
        assertThat(same(a, again)).isTrue();
        assertThat(same(a, accents)).isTrue(); // mayúsculas y tildes no cambian el diseño
        assertThat(same(a, other)).isFalse();
        assertThat(a.getWidth()).isEqualTo(1080);
        assertThat(a.getHeight()).isEqualTo(1350);
    }

    @Test
    void differentTitlesUseSeveralTemplatesAndEveryTemplateRenders() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 60; i++) used.add(FallbackBackground.template("Canción número " + i));
        assertThat(used).hasSize(FallbackBackground.TEMPLATES);
        // Cada plantilla se pinta sin fallar y no sale lisa
        for (int t = 0; t < 400 && !used.isEmpty(); t++) {
            String title = "Prueba " + t;
            if (!used.remove(FallbackBackground.template(title))) continue;
            var image = FallbackBackground.render(540, 675, "#D23A2E", title, null, 0.5);
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < 675; y += 15) for (int x = 0; x < 540; x += 15) colors.add(image.getRGB(x, y));
            assertThat(colors.size()).as("plantilla de «%s»", title).isGreaterThan(1);
        }
    }

    @Test
    void theBackgroundWithoutTitleDiffersFromTheOneWithIt() {
        var plain = FallbackBackground.render(1080, 1920, "#FF8000", "Con púa", null, 0.5);
        var titled = FallbackBackground.render(1080, 1920, "#FF8000", "Con púa", "Con púa", 0.5);
        assertThat(same(plain, titled)).isFalse();
    }

    @Test
    void theTitlePanelAlwaysReadsWithAtLeastSevenToOneForAnyBrandColor() {
        for (String hex : List.of("#FF8000", "#D23A2E", "#FFFF00", "#FFFFFF", "#000000", "#00FF00", "#C98A1B", "#336699", "no es un color")) {
            Color panel = FallbackBackground.readablePanel(FallbackBackground.parse(hex));
            assertThat(FallbackBackground.contrast(Drawing.LIGHT, panel)).as(hex).isGreaterThanOrEqualTo(7.0);
        }
    }

    @Test
    void theTitleIsDrawnOnTheDarkPanel() {
        var image = FallbackBackground.render(1080, 1350, "#FFFF00", "Un título", "Un título", 0.5);
        // La banda del título está oscura aunque el color de la marca sea clarísimo
        Color panel = new Color(image.getRGB(5, 675));
        assertThat(FallbackBackground.contrast(Drawing.LIGHT, panel)).isGreaterThanOrEqualTo(4.5);
        // y hay texto claro dentro de la banda
        boolean light = false;
        for (int y = 560; y < 790 && !light; y++) for (int x = 100; x < 980; x += 2) {
            if (FallbackBackground.contrast(Drawing.LIGHT, new Color(image.getRGB(x, y))) < 1.6) { light = true; break; }
        }
        assertThat(light).isTrue();
    }
}
