package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Los informes de fallo de la JVM se recogen (solo los de SocialHub) y no se acumulan. */
class JvmCrashReportsTest {

    @Test
    void recogeSoloLosDeSocialHubYBorraLosViejosYElExceso(@TempDir Path dir) throws IOException {
        Path logs = dir.resolve("logs");
        Path temp = dir.resolve("tmp");
        Files.createDirectories(temp);
        Files.writeString(temp.resolve("hs_err_pid111.log"), "# A fatal error\n# Command Line: C:\\Program Files\\SocialHub\\app\\socialhub.jar\n");
        Files.writeString(temp.resolve("hs_err_pid222.log"), "# A fatal error\n# Command Line: otro-programa.jar\n");
        Files.createDirectories(logs);
        Path old = logs.resolve("hs_err_pid1.log");
        Files.writeString(old, "viejo");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(90, ChronoUnit.DAYS)));

        List<Path> kept = JvmCrashReports.collect(logs, List.of(temp, dir.resolve("no-existe")));

        assertThat(kept).extracting(p -> p.getFileName().toString()).containsExactly("hs_err_pid111.log");
        assertThat(temp.resolve("hs_err_pid222.log")).exists();
        assertThat(temp.resolve("hs_err_pid111.log")).doesNotExist();
        assertThat(old).doesNotExist();
    }

    @Test
    void conservaSoloLosDiezUltimos(@TempDir Path dir) throws IOException {
        for (int i = 0; i < 13; i++) {
            Path f = dir.resolve("hs_err_pid" + i + ".log");
            Files.writeString(f, "x");
            Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(i, ChronoUnit.HOURS)));
        }
        assertThat(JvmCrashReports.collect(dir, List.of())).hasSize(10);
    }
}
