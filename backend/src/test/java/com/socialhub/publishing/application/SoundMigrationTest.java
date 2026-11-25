package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.postgres.ModeDataSources;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La migración V36 añade el sonido a las publicaciones y la página y el archivo de origen a sus archivos: las filas que ya
 * existían quedan «sin sonido» y sin página ni origen. Se migra un esquema aparte hasta V35, se dejan filas como las de antes
 * y se aplica el resto.
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL"
})
class SoundMigrationTest {

    private static final String USER = com.socialhub.platform.postgres.EmbeddedPostgres.USER;

    @Autowired ModeDataSources databases;

    @Test
    void existingPublicationsAndFilesSurviveV36WithoutSoundPageOrSource() throws Exception {
        String url = databases.jdbcUrl(AppMode.REAL);
        flyway(url, "35").migrate();
        try (Connection c = DriverManager.getConnection(url, databases.user(), ""); Statement st = c.createStatement()) {
            st.execute("set search_path to mig_sound");
            st.execute("alter table publication drop constraint publication_promotion_id_fkey");
            st.execute("insert into publication (id, promotion_id, platform, type, title, scheduled_on, scheduled_time, status)"
                    + " values ('p-old', 'no-promo', 'TIKTOK', 'Reel', 'Antigua', '2026-11-02', '19:00', 'APPROVED')");
            st.execute("insert into publication_file (publication_id, path, origin) values ('p-old', '/x/pieza.jpg', 'SPEC')");
        }

        flyway(url, null).migrate();

        try (Connection c = DriverManager.getConnection(url, databases.user(), ""); Statement st = c.createStatement()) {
            st.execute("set search_path to mig_sound");
            try (ResultSet rs = st.executeQuery("select sound_mode, sound_track from publication where id = 'p-old'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("NONE");
                assertThat(rs.getString(2)).isNull();
            }
            try (ResultSet rs = st.executeQuery("select page, source_name, origin from publication_file where publication_id = 'p-old'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1)).isNull();
                assertThat(rs.getString(2)).isNull();
                assertThat(rs.getString(3)).isEqualTo("SPEC");
            }
        }
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, USER, "").schemas("mig_sound").createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
}
