package com.socialhub.launcher.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.launcher.domain.TrayMenu.Action;
import com.socialhub.launcher.domain.TrayMenu.Entry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El menú de la bandeja: abrir, registros, arranque con el sistema (si el sistema lo permite), cerrar y la versión como texto. */
class TrayMenuTest {

    @Test
    void hasTheVersionAsNonClickableTextThenOpenLogsAutostartAndQuit() {
        List<Entry> menu = TrayMenu.entries("1.2.7", true, true);
        assertThat(menu).extracting(Entry::label).containsExactly("SocialHub 1.2.7", "", "Abrir SocialHub",
                "Abrir carpeta de registros", "Abrir al iniciar sesión", "", "Cerrar SocialHub");
        assertThat(menu.getFirst().kind()).isEqualTo(Entry.Kind.TEXT);
        assertThat(menu.getFirst().action()).as("la versión no es pulsable").isNull();
        assertThat(menu).filteredOn(e -> e.kind() == Entry.Kind.ACTION).extracting(Entry::action)
                .containsExactly(Action.OPEN, Action.OPEN_LOGS, Action.QUIT);
        Entry toggle = menu.get(4);
        assertThat(toggle.kind()).isEqualTo(Entry.Kind.CHECK);
        assertThat(toggle.checked()).isTrue();
        assertThat(toggle.action()).isEqualTo(Action.TOGGLE_AUTOSTART);
    }

    @Test
    void withoutAutostartSupportThereIsNoSwitchAndTheSwitchShowsTheCurrentState() {
        assertThat(TrayMenu.entries("1.0.0", false, false)).extracting(Entry::kind).doesNotContain(Entry.Kind.CHECK);
        assertThat(TrayMenu.entries("1.0.0", true, false).stream().filter(e -> e.kind() == Entry.Kind.CHECK).findFirst().orElseThrow().checked())
                .isFalse();
    }

    @Test
    void theTooltipIsSocialHub() {
        assertThat(TrayMenu.TOOLTIP).isEqualTo("SocialHub");
    }
}
