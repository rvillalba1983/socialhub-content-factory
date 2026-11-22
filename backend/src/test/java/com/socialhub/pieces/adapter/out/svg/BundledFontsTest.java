package com.socialhub.pieces.adapter.out.svg;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BundledFontsTest {

    private static final String SPANISH = "áéíóúüñÁÉÍÓÚÜÑ¿¡«»“”‘’—–…€%·º";

    static Stream<Arguments> variants() {
        return Stream.of(
                Arguments.of("Crimson Text", "normal", "normal", "Crimson Text Regular"),
                Arguments.of("Crimson Text", "normal", "italic", "Crimson Text Italic"),
                Arguments.of("Crimson Text", "600", "normal", "Crimson Text SemiBold"),
                Arguments.of("Crimson Text", "bold", "normal", "Crimson Text Bold"),
                Arguments.of("Fira Sans", "normal", "normal", "Fira Sans Regular"),
                Arguments.of("Fira Sans", "normal", "italic", "Fira Sans Italic"),
                Arguments.of("Fira Sans", "500", "normal", "Fira Sans Medium"),
                Arguments.of("Fira Sans", "bold", "normal", "Fira Sans Bold"),
                Arguments.of("Bebas Neue", "normal", "normal", "Bebas Neue Regular"));
    }

    @ParameterizedTest
    @MethodSource("variants")
    void resolvesEveryVariantAndItHasTheSpanishGlyphs(String family, String weight, String style, String expectedName) {
        Font font = BundledFonts.resolve(family, weight, style).orElseThrow();
        assertThat(font.getFontName()).isEqualTo(expectedName);
        assertThat(SPANISH.codePoints().filter(cp -> !font.canDisplay(cp)).toArray()).isEmpty();
    }

    @Test
    void picksTheClosestWeightAndTheHeavierOnATie() {
        assertThat(BundledFonts.resolve("Fira Sans", "300", "normal").orElseThrow().getFontName()).isEqualTo("Fira Sans Regular");
        assertThat(BundledFonts.resolve("Fira Sans", "600", "normal").orElseThrow().getFontName()).isEqualTo("Fira Sans Bold");
        assertThat(BundledFonts.resolve("Fira Sans", "900", "normal").orElseThrow().getFontName()).isEqualTo("Fira Sans Bold");
        assertThat(BundledFonts.resolve("Crimson Text", "500", "normal").orElseThrow().getFontName()).isEqualTo("Crimson Text SemiBold");
        assertThat(BundledFonts.resolve("Bebas Neue", "700", "normal").orElseThrow().getFontName()).isEqualTo("Bebas Neue Regular");
    }

    @Test
    void aMissingItalicFallsBackToTheUprightFont() {
        assertThat(BundledFonts.resolve("Bebas Neue", "normal", "italic").orElseThrow().getFontName()).isEqualTo("Bebas Neue Regular");
    }

    @Test
    void familyIsTheFirstOfTheListWithoutQuotesAndIgnoringCase() {
        assertThat(BundledFonts.resolve("'fira sans', Arial, sans-serif", "normal", "normal")).isPresent();
        assertThat(BundledFonts.resolve("\"CRIMSON TEXT\"", "normal", "normal")).isPresent();
        assertThat(BundledFonts.resolve("Arial, Fira Sans", "normal", "normal")).isEmpty();
        assertThat(BundledFonts.resolve("DejaVu Serif", "normal", "normal")).isEmpty();
        assertThat(BundledFonts.families()).containsExactly("Crimson Text", "Fira Sans", "Bebas Neue");
    }

    @Test
    void theSameFontInstanceIsLoadedOnlyOnce() {
        assertThat(BundledFonts.resolve("Fira Sans", "normal", "normal").orElseThrow())
                .isSameAs(BundledFonts.resolve("fira sans", "400", "normal").orElseThrow());
    }
}
