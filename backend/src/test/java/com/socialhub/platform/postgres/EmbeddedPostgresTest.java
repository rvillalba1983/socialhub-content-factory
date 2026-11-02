package com.socialhub.platform.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.observability.ExternalCalls;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** El gestor propio arranca, para y vuelve a arrancar PostgreSQL sin perder datos. */
class EmbeddedPostgresTest {

    /**
     * Bajo target/ y no en una carpeta temporal de JUnit: en Windows, PostgreSQL tarda un momento en soltar sus logs
     * tras pararse y JUnit fallaba al borrar la carpeta (el servidor ya estaba parado y los datos, bien).
     */
    private final Path dir = Path.of("target", "it", "pg-" + java.util.UUID.randomUUID()).toAbsolutePath();

    @Test
    void dataSurvivesARestart() throws Exception {
        PostgresBinaries binaries = PostgresBinaries.resolve("", Path.of("target", "pg-runtime").toAbsolutePath());
        EmbeddedPostgres pg = new EmbeddedPostgres(binaries, dir.resolve("pgdata"), dir.resolve("logs"), 0, 60,
                ExternalCalls.unobserved());
        pg.start();
        try {
            try (Connection c = DriverManager.getConnection(pg.jdbcUrl(), EmbeddedPostgres.USER, "")) {
                c.createStatement().execute("create table nota (texto text)");
                c.createStatement().execute("insert into nota values ('SocialHub')");
            }
        } finally {
            pg.stop();
        }
        assertThat(pg.isRunning()).isFalse();

        pg.start();
        try (Connection c = DriverManager.getConnection(pg.jdbcUrl(), EmbeddedPostgres.USER, "");
             ResultSet rs = c.createStatement().executeQuery("select texto from nota")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("SocialHub");
        } finally {
            pg.stop();
        }
    }
}
