package com.socialhub.pieces.adapter.out.svg;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import org.junit.jupiter.api.Test;

class TextFitterTest {

    private final Font base = BundledFonts.resolve("Fira Sans", "normal", "normal").orElseThrow();

    private TextFitter.Result fit(String text, double w, double h, int max, int min, double lh, int maxLines) {
        return TextFitter.fit(text, base, w, h, max, min, lh, maxLines, 0);
    }

    @Test
    void shortTextKeepsTheMaximumSizeInOneLine() {
        var result = fit("Hola mundo", 800, 300, 60, 20, 1.2, 30);
        assertThat(result.size()).isEqualTo(60);
        assertThat(result.lines()).containsExactly("Hola mundo");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void wrapsByWordsAndEveryLineFitsTheWidth() {
        String text = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince";
        var result = fit(text, 400, 1000, 50, 20, 1.2, 30);
        Font font = TextFitter.derive(base, result.size(), 0);
        assertThat(result.lines().size()).isGreaterThan(1);
        assertThat(result.lines()).allSatisfy(l -> assertThat(TextFitter.width(l, font)).isLessThanOrEqualTo(400));
        assertThat(String.join(" ", result.lines())).isEqualTo(text);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void shrinksOneByOneUntilTheBlockFitsTheHeight() {
        String text = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince";
        var tall = fit(text, 400, 1000, 50, 20, 1.2, 30);
        var low = fit(text, 400, 200, 50, 20, 1.2, 30);
        assertThat(low.size()).isLessThan(tall.size());
        assertThat(low.lines().size() * 1.2 * low.size()).isLessThanOrEqualTo(200);
        // El siguiente tamaño mayor no habría cabido: baja de 1 en 1, no a saltos
        var bigger = TextFitter.wrap(text, TextFitter.derive(base, low.size() + 1, 0), 400, false);
        assertThat(bigger.size() * 1.2 * (low.size() + 1) > 200 || low.size() == 50).isTrue();
    }

    @Test
    void keepsParagraphsAndBlankLinesAndNormalizesCrLf() {
        var result = fit("Verso uno\r\nVerso dos\n\nOtra estrofa", 800, 800, 40, 20, 1.2, 30);
        assertThat(result.lines()).containsExactly("Verso uno", "Verso dos", "", "Otra estrofa");
    }

    @Test
    void trailingAndLeadingBlankLinesAreDropped() {
        assertThat(fit("\n\nHola\n\n", 800, 800, 40, 20, 1.2, 30).lines()).containsExactly("Hola");
    }

    @Test
    void emptyTextGivesNoLines() {
        var result = fit("  ", 800, 800, 40, 20, 1.2, 30);
        assertThat(result.lines()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void aWordWiderThanTheBoxIsSplitByCharactersAtTheMinimumSize() {
        var result = fit("Supercalifragilisticoespialidoso", 120, 600, 40, 20, 1.2, 30);
        Font font = TextFitter.derive(base, result.size(), 0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.lines().size()).isGreaterThan(1);
        assertThat(String.join("", result.lines())).isEqualTo("Supercalifragilisticoespialidoso");
        assertThat(result.lines()).allSatisfy(l -> assertThat(TextFitter.width(l, font)).isLessThanOrEqualTo(120));
        assertThat(result.truncated()).isFalse();
        assertThat(result.wordsSplit()).isTrue();
    }

    @Test
    void normalTextNeverReportsSplitWords() {
        assertThat(fit("Hola mundo", 800, 300, 60, 20, 1.2, 30).wordsSplit()).isFalse();
    }

    @Test
    void hugeInputStopsWrappingEarlyAndStaysFast() {
        long start = System.nanoTime();
        var result = fit("palabra ".repeat(2000), 300, 100, 40, 20, 1.2, 30);
        assertThat(result.truncated()).isTrue();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void maxLinesIsRespectedByShrinkingFirst() {
        String text = "uno dos tres cuatro cinco seis siete ocho nueve diez";
        var result = fit(text, 500, 1000, 60, 20, 1.2, 2);
        assertThat(result.lines().size()).isLessThanOrEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void whenNothingFitsItTruncatesWithAnEllipsisThatFits() {
        String text = "palabra ".repeat(200);
        var result = fit(text, 300, 100, 40, 20, 1.2, 30);
        Font font = TextFitter.derive(base, result.size(), 0);
        assertThat(result.truncated()).isTrue();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.lines().get(result.lines().size() - 1)).endsWith("…");
        assertThat(result.lines()).allSatisfy(l -> assertThat(TextFitter.width(l, font)).isLessThanOrEqualTo(300));
        assertThat(result.lines().size() * 1.2 * 20).isLessThanOrEqualTo(100);
    }

    @Test
    void maxLinesAlsoTruncatesAtTheMinimumSize() {
        var result = fit("palabra ".repeat(100), 300, 1000, 40, 30, 1.2, 2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(1)).endsWith("…");
    }

    @Test
    void letterSpacingWidensTheLines() {
        Font plain = TextFitter.derive(base, 40, 0);
        Font spaced = TextFitter.derive(base, 40, 10);
        assertThat(TextFitter.width("Hola mundo", spaced)).isGreaterThan(TextFitter.width("Hola mundo", plain) + 50);
    }
}
