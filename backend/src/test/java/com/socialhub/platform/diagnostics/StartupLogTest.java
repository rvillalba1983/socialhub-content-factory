package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** arranque.log: se crea, recoge la salida de la JVM, apunta las fases y los fallos, y no deja pasar secretos. */
class StartupLogTest {

    private final PrintStream out = System.out;
    private final PrintStream err = System.err;

    @AfterEach
    void restore() {
        StartupLog.reset();
        System.setOut(out);
        System.setErr(err);
    }

    @Test
    void sinInicializarNoEscribeNada(@TempDir Path dir) {
        StartupLog.phase("no debería ir a ningún sitio");
        assertThat(StartupLog.file()).isNull();
        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void recogeFasesFallosYSalidaEstandar(@TempDir Path dir) throws IOException {
        Path logs = dir.resolve("logs");
        assertThat(StartupLog.init(logs, true)).isEqualTo(logs.resolve("arranque.log"));
        StartupLog.phase("PostgreSQL: en marcha en el puerto 54329");
        StartupLog.error("Flyway falló", new IllegalStateException("migración rota, password=hunter2"));
        System.out.println("salida normal con token=sk-AAAAAAAAAAAAAAAAAAAAAAAA");
        System.err.println("salida de error");
        new RuntimeException("traza suelta").printStackTrace();

        String text = Files.readString(logs.resolve("arranque.log"));
        assertThat(text).contains("[arranque] PostgreSQL: en marcha en el puerto 54329")
                .contains("[ERROR] Flyway falló").contains("IllegalStateException: migración rota")
                .contains("salida normal con token=***").contains("salida de error").contains("RuntimeException: traza suelta");
        assertThat(text).doesNotContain("hunter2").doesNotContain("sk-AAAA");
    }

    @Test
    void sinRedirigirLaConsolaNoLaToca(@TempDir Path dir) {
        StartupLog.init(dir, false);
        assertThat(System.out).isSameAs(out);
    }

    @Test
    void apartaElArchivoGrandeSinFallarSiNoPuede(@TempDir Path dir) throws IOException {
        Path big = dir.resolve("postgres.log");
        Files.writeString(big, "x".repeat(2000));
        StartupLog.rotateIfBig(big, 1000);
        assertThat(big).doesNotExist();
        assertThat(dir.resolve("postgres.1.log")).exists();
        StartupLog.rotateIfBig(dir.resolve("no-existe.log"), 1000);
    }
}
