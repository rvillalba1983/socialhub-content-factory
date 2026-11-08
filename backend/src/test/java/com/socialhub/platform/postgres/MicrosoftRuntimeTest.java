package com.socialhub.platform.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Las bibliotecas de Visual C++ de Java se copian junto a PostgreSQL sin pisar las que ya haya. */
class MicrosoftRuntimeTest {

    @Test
    void copiesTheMissingLibrariesFromJava(@TempDir Path tmp) throws Exception {
        Path java = Files.createDirectories(tmp.resolve("java"));
        Path pg = Files.createDirectories(tmp.resolve("pg"));
        Files.writeString(java.resolve("vcruntime140.dll"), "java");
        Files.writeString(java.resolve("msvcp140.dll"), "java");
        Files.writeString(pg.resolve("msvcp140.dll"), "propia");

        PostgresBinaries.addMicrosoftRuntime(pg, java);

        assertThat(pg.resolve("vcruntime140.dll")).hasContent("java");
        assertThat(pg.resolve("msvcp140.dll")).hasContent("propia");
        assertThat(pg.resolve("vcruntime140_1.dll")).doesNotExist();
    }
}
