package com.socialhub.launcher.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.launcher.Launcher;
import com.socialhub.launcher.domain.AutostartEntry;
import com.socialhub.launcher.domain.AutostartPreference;
import com.socialhub.launcher.domain.HostShell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Arranque con el sistema (por defecto activado, con opción de desactivarlo) y las acciones del icono. */
class LauncherServiceTest {

    boolean supported = true;
    boolean registered = false;
    boolean failRegister = false;
    Boolean preferred = null;
    final List<String> calls = new ArrayList<>();

    final AutostartEntry entry = new AutostartEntry() {
        @Override public boolean supported() { return supported; }
        @Override public boolean exists() { return registered; }
        @Override public void register() { if (failRegister) throw new IllegalStateException("sin permiso"); registered = true; }
        @Override public void unregister() { registered = false; }
    };
    final AutostartPreference preference = new AutostartPreference() {
        @Override public Optional<Boolean> get() { return Optional.ofNullable(preferred); }
        @Override public void set(boolean enabled) { preferred = enabled; }
    };
    final HostShell shell = new HostShell() {
        @Override public void openBrowser() { calls.add("browser"); }
        @Override public void openLogsFolder() { calls.add("logs"); }
        @Override public void exit() { calls.add("exit"); }
        @Override public String version() { return "1.2.7"; }
    };
    final Launcher launcher = new LauncherService(entry, preference, shell);

    @Test
    void theFirstStartActivatesItByDefaultAndRemembersIt() {
        launcher.syncAutostart();
        assertThat(registered).isTrue();
        assertThat(preferred).isTrue();
        assertThat(launcher.autostart()).isEqualTo(new Launcher.Autostart(true, true));
    }

    @Test
    void ifRaulTurnedItOffItStaysOffAndTheEntryIsRemovedEvenIfSomethingLeftItBehind() {
        registered = true;
        preferred = false;
        launcher.syncAutostart();
        assertThat(registered).isFalse();
        assertThat(preferred).isFalse();
    }

    @Test
    void whenItWasOnTheEntryIsRewrittenEachStartSoItPointsToTheCurrentProgram() {
        preferred = true;
        registered = false; // p. ej. tras reinstalar en otra carpeta
        launcher.syncAutostart();
        assertThat(registered).isTrue();
    }

    @Test
    void theSwitchTurnsItOnAndOffAndKeepsThePreference() {
        assertThat(launcher.setAutostart(false)).isEqualTo(new Launcher.Autostart(true, false));
        assertThat(preferred).isFalse();
        assertThat(launcher.setAutostart(true).enabled()).isTrue();
        assertThat(registered).isTrue();
    }

    @Test
    void whereItIsNotSupportedNothingHappensAndTheSwitchSaysWhy() {
        supported = false;
        launcher.syncAutostart();
        assertThat(registered).isFalse();
        assertThat(preferred).isNull();
        assertThat(launcher.autostart()).isEqualTo(new Launcher.Autostart(false, false));
        assertThatThrownBy(() -> launcher.setAutostart(true)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Windows");
    }

    @Test
    void aFailureWritingTheEntryNeverStopsSocialHubAtStart() {
        failRegister = true;
        launcher.syncAutostart();
        assertThat(registered).isFalse();
    }

    @Test
    void openingQuittingAndTheVersionGoThroughTheShell() {
        launcher.openApp();
        launcher.openLogs();
        launcher.quit();
        assertThat(calls).containsExactly("browser", "logs", "exit");
        assertThat(launcher.version()).isEqualTo("1.2.7");
    }
}
