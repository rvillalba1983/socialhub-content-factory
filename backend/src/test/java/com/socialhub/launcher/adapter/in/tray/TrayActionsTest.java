package com.socialhub.launcher.adapter.in.tray;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.launcher.Launcher;
import com.socialhub.launcher.domain.TrayMenu.Action;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cada línea del menú de la bandeja hace lo suyo (sin AWT). */
class TrayActionsTest {

    final List<String> calls = new ArrayList<>();
    boolean autostart = true;

    final Launcher launcher = new Launcher() {
        @Override public Autostart autostart() { return new Autostart(true, autostart); }
        @Override public Autostart setAutostart(boolean enabled) { autostart = enabled; calls.add("autostart=" + enabled); return autostart(); }
        @Override public void syncAutostart() { calls.add("sync"); }
        @Override public void openApp() { calls.add("open"); }
        @Override public void openLogs() { calls.add("logs"); }
        @Override public void quit() { calls.add("quit"); }
        @Override public String version() { return "1.0.0"; }
    };
    final TrayActions actions = new TrayActions(launcher);

    @Test
    void openLogsAndQuitCallTheirPorts() {
        assertThat(actions.perform(Action.OPEN)).isNull();
        assertThat(actions.perform(Action.OPEN_LOGS)).isNull();
        assertThat(actions.perform(Action.QUIT)).isNull();
        assertThat(calls).containsExactly("open", "logs", "quit");
    }

    @Test
    void theSwitchFlipsTheAutostartAndReturnsTheNewState() {
        assertThat(actions.perform(Action.TOGGLE_AUTOSTART)).isFalse();
        assertThat(actions.perform(Action.TOGGLE_AUTOSTART)).isTrue();
        assertThat(calls).containsExactly("autostart=false", "autostart=true");
    }
}
