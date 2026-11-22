package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CitationMatcherTest {

    @Test
    void ignoresSpacesLineBreaksQuotesDashesAndAccents() {
        List<String> parts = List.of("Portada", "Cuando el mar \"calla\"\ny el cielo — sin   nombre");
        assertThat(CitationMatcher.find(parts, "cuando el mar “calla” y el cielo – sin nombre")).isEmpty(); // las mayúsculas cuentan
        assertThat(CitationMatcher.find(parts, "Cuando el mar “calla” y el cielo – sin nombre")).hasValue(1);
        assertThat(CitationMatcher.find(List.of("Mi corazón late"), "Mi corazon late")).hasValue(0);
        assertThat(CitationMatcher.find(List.of("Mi corazon late"), "Mi corazón late")).hasValue(0);
    }

    @Test
    void doesNotInventTextAndRejectsEmptyCitations() {
        assertThat(CitationMatcher.find(List.of("la noche se abre"), "la noche se cierra")).isEmpty();
        assertThat(CitationMatcher.find(List.of("la noche se abre"), "   ")).isEmpty();
        assertThat(CitationMatcher.find(List.of(), "algo")).isEmpty();
    }

    @Test
    void joinsWordsSplitByAHyphenAtTheEndOfALine() {
        assertThat(CitationMatcher.find(List.of("una pala-\nbra partida"), "una palabra partida")).hasValue(0);
    }

    @Test
    void findsACitationThatCrossesTwoPages() {
        assertThat(CitationMatcher.find(List.of("primeros versos de la noche", "que siguen en la otra"), "de la noche que siguen")).hasValue(0);
    }

    @Test
    void foldsLigaturesFromPdfs() {
        assertThat(CitationMatcher.find(List.of("el ﬁn del camino"), "el fin del camino")).hasValue(0);
    }

    @Test
    void locatesTheLineInPlainText() {
        String text = "Primer poema\nla noche se abre\ncomo una herida\n";
        assertThat(CitationMatcher.lineOf(text, "la noche se abre")).isEqualTo(2);
        assertThat(CitationMatcher.lineOf(text, "se abre como una")).isEqualTo(2);
        assertThat(CitationMatcher.lineOf(text, "no está")).isZero();
    }
}
