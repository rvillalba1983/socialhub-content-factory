package com.socialhub.platform.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PostgreSQL descomprimido a medias se vuelve a descomprimir: la marca lista sus archivos y se comprueban al arrancar. */
class PostgresExtractionTest {

    @Test
    void aMissingFileOrAnOldMarkerMeansExtractAgain(@TempDir Path tmp) throws Exception {
        Path marker = tmp.resolve(".complete");
        assertThat(PostgresBinaries.complete(tmp, marker)).isFalse();

        Files.writeString(marker, "ok");
        assertThat(PostgresBinaries.complete(tmp, marker)).isFalse();

        Files.createDirectories(tmp.resolve("bin"));
        Files.writeString(tmp.resolve("bin/postgres.exe"), "x");
        Files.writeString(tmp.resolve("bin/zlib1.dll"), "x");
        Files.writeString(marker, "bin/postgres.exe\nbin/zlib1.dll\n");
        assertThat(PostgresBinaries.complete(tmp, marker)).isTrue();

        Files.delete(tmp.resolve("bin/zlib1.dll"));
        assertThat(PostgresBinaries.complete(tmp, marker)).isFalse();
    }
}
