package com.socialhub.platform.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El arranque en segundo plano (el de abrirse al iniciar sesión) no abre el navegador; el arranque a mano sí. */
class BackgroundStartTest {

    @AfterEach
    void clean() {
        System.clearProperty("socialhub.background");
        System.clearProperty("socialhub.desktop");
    }

    @Test
    void theFlagMarksABackgroundStartAndNothingElseDoes() {
        Desktop.readArguments(new String[] {"--spring.foo=bar"});
        assertThat(Desktop.background()).isFalse();
        Desktop.readArguments(new String[] {"--background"});
        assertThat(Desktop.background()).isTrue();
    }

    @Test
    void theBrowserOnlyOpensOnAManualStart() {
        // BrowserOnStart abre con Desktop.openBrowser(): aquí solo se comprueba su condición con las mismas marcas
        System.setProperty("socialhub.desktop", "true");
        assertThat(Desktop.enabled() && !Desktop.background()).as("arranque a mano").isTrue();
        System.setProperty("socialhub.background", "true");
        assertThat(Desktop.enabled() && !Desktop.background()).as("al iniciar sesión").isFalse();
    }
}
