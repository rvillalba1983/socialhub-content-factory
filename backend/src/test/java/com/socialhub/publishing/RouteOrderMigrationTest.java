package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.postgres.ModeDataSources;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * La migración V23 cambia el orden de las vías (antes Publer, Publora, API oficial; ahora API oficial, Publer, Publora):
 * los trabajos en curso conservan la vía que tenían. Se migra un esquema aparte hasta V22, se dejan trabajos como los de
 * antes y se aplica el resto.
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL"
})
class RouteOrderMigrationTest {

    private static final String USER = com.socialhub.platform.postgres.EmbeddedPostgres.USER;

    @Autowired ModeDataSources databases;

    @Test
    void jobsInProgressKeepTheirRouteWhenTheOrderChanges() throws Exception {
        String url = databases.jdbcUrl(AppMode.REAL);
        Flyway before = flyway(url, "22");
        before.migrate();
        try (Connection c = DriverManager.getConnection(url, databases.user(), ""); Statement st = c.createStatement()) {
            st.execute("set search_path to mig_route");
            // Sin las publicaciones de verdad: solo importan los trabajos
            st.execute("alter table publish_job drop constraint publish_job_publication_id_fkey");
            insert(st, "p-publer", "WORKING", 0, "Publer");
            insert(st, "p-publora", "WORKING", 1, "Publora");
            insert(st, "p-sched", "SCHEDULED", 0, "Publer");
            insert(st, "p-sched-publora", "SCHEDULED", 1, "Publora");
            insert(st, "p-checking", "CHECKING", 0, "Publer");
            insert(st, "p-manual", "MANUAL", 3, "A mano");
            insert(st, "p-retry", "RETRY_WAIT", 0, null);
            insert(st, "p-done", "PUBLISHED", 0, "Publer");
        }

        Flyway after = flyway(url, null);
        after.migrate();

        try (Connection c = DriverManager.getConnection(url, databases.user(), ""); Statement st = c.createStatement()) {
            st.execute("set search_path to mig_route");
            // Publer pasa del índice 0 al 1 y Publora del 1 al 2: siguen en la misma vía
            assertThat(index(st, "p-publer")).isEqualTo(1);
            assertThat(index(st, "p-publora")).isEqualTo(2);
            assertThat(index(st, "p-sched")).isEqualTo(1);
            assertThat(index(st, "p-sched-publora")).isEqualTo(2);
            assertThat(index(st, "p-checking")).isEqualTo(1);
            assertThat(index(st, "p-manual")).isEqualTo(4);
            // Un reintento vuelve a empezar por la primera vía; uno terminado no importa
            assertThat(index(st, "p-retry")).isZero();
            assertThat(index(st, "p-done")).isZero();
            // Y la columna nueva existe
            ResultSet rs = st.executeQuery("select count(*) from information_schema.columns where table_schema = 'mig_route'"
                    + " and table_name = 'publish_job' and column_name in ('publish_started_at', 'preflight_at')");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, USER, "").schemas("mig_route").createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private static void insert(Statement st, String id, String status, int index, String route) throws SQLException {
        st.execute("insert into publish_job (publication_id, status, route_index, route) values ('" + id + "', '" + status + "', "
                + index + ", " + (route == null ? "null" : "'" + route + "'") + ")");
    }

    private static int index(Statement st, String id) throws SQLException {
        try (ResultSet rs = st.executeQuery("select route_index from publish_job where publication_id = '" + id + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
