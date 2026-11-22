package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextLinksTest {

    @Test
    void findsLinksWithoutTheirSurroundingPunctuationAndWithoutRepeats() {
        String text = "Mira https://litoques.es/libro. Y (http://a.org/x), o «https://litoques.es/libro»; también https://b.org/a_(b)!";
        assertThat(TextLinks.find(text)).containsExactly("https://litoques.es/libro", "http://a.org/x", "https://b.org/a_(b)");
    }

    @Test
    void noLinksInPlainTextOrNull() {
        assertThat(TextLinks.find("sin enlaces www.litoques.es")).isEmpty();
        assertThat(TextLinks.find(null)).isEmpty();
        assertThat(TextLinks.https("HTTPS://x.es")).isTrue();
        assertThat(TextLinks.https("http://x.es")).isFalse();
    }
}
