package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Abrir la carpeta de registros usa el explorador de cada sistema, sin intérprete de órdenes. */
class LogsFolderTest {

    @Test
    void ordenPorSistema() {
        Path p = Path.of("/datos/SocialHub logs").toAbsolutePath();
        assertThat(LogsFolder.command("Windows 11", p)).containsExactly("explorer.exe", p.toString());
        assertThat(LogsFolder.command("Mac OS X", p)).containsExactly("open", p.toString());
        assertThat(LogsFolder.command("Linux", p)).containsExactly("xdg-open", p.toString());
    }
}
