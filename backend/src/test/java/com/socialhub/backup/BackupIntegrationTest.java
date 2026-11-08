package com.socialhub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.postgres.ModeDataSources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Copia de la base real, cambio de datos y restauración: vuelven los datos de la copia y SocialHub queda parado. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL"
})
@AutoConfigureMockMvc
@DirtiesContext
class BackupIntegrationTest {

    private static final String GUARD = "X-SocialHub-Request";

    @Autowired MockMvc mvc;
    @Autowired ModeDataSources databases;

    @AfterEach
    void resume() { AppModeHolder.resume(); }

    @Test
    void backupAndRestoreBringBackTheSavedData() throws Exception {
        sql("insert into app_setting (key, value) values ('prueba.marca', 'antes')");
        long sequenceBefore = number("select nextval('revinfo_seq')");

        // Sin carpeta elegida, la copia va a la carpeta local
        String body = mvc.perform(post("/api/backups").header(GUARD, "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MANUAL"))
                .andExpect(jsonPath("$.local").value(true))
                .andReturn().getResponse().getContentAsString();
        String name = JsonMapper.builder().build().readTree(body).get("name").asString();

        mvc.perform(get("/api/backups"))
                .andExpect(jsonPath("$.items[0].name").value(name))
                .andExpect(jsonPath("$.folder_available").value(false))
                .andExpect(jsonPath("$.keep").value(14));
        String local = JsonMapper.builder().build()
                .readTree(mvc.perform(get("/api/backups")).andReturn().getResponse().getContentAsString())
                .get("local_folder").asString();
        try (ZipFile zip = new ZipFile(Path.of(local, name).toFile())) {
            assertThat(zip.getEntry("manifest.json")).isNotNull();
            assertThat(zip.getEntry("tablas/app_setting.csv")).isNotNull();
            assertThat(zip.getEntry("tablas/flyway_schema_history.csv")).isNull();
        }

        // Cambios después de la copia
        sql("update app_setting set value = 'despues' where key = 'prueba.marca'");
        sql("insert into app_setting (key, value) values ('prueba.nueva', 'x')");
        number("select nextval('revinfo_seq')");

        // Sin escribir bien el nombre no se restaura
        mvc.perform(post("/api/backups/restorations").header(GUARD, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"confirmation\":\"otra\"}"))
                .andExpect(status().isConflict());

        // La carpeta elegida tiene que existir
        mvc.perform(put("/api/backups/folder").header(GUARD, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folder\":\"/no/existe/en/ningun/sitio\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/backups/restorations").header(GUARD, "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"confirmation\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored").value(name))
                .andExpect(jsonPath("$.safety_backup").value(org.hamcrest.Matchers.endsWith("-antes-de-restaurar.zip")));

        // Vuelven los datos de la copia, las secuencias siguen donde estaban y el esquema temporal no queda
        assertThat(text("select value from app_setting where key = 'prueba.marca'")).isEqualTo("antes");
        assertThat(number("select count(*) from app_setting where key = 'prueba.nueva'")).isZero();
        assertThat(number("select nextval('revinfo_seq')")).isGreaterThan(sequenceBefore);
        assertThat(number("select count(*) from pg_namespace where nspname = 'restore_tmp'")).isZero();
        assertThat(number("select count(*) from flyway_schema_history where success")).isPositive();
        assertThat(Files.list(Path.of(local)).filter(p -> p.getFileName().toString().endsWith("-antes-de-restaurar.zip")).count()).isEqualTo(1);

        // SocialHub queda parado: no acepta cambios y la interfaz lo sabe
        mvc.perform(get("/api/app-mode")).andExpect(jsonPath("$.halted_reason").value(org.hamcrest.Matchers.containsString(name)));
        mvc.perform(post("/api/backups").header(GUARD, "1")).andExpect(status().isServiceUnavailable());
    }

    // Conexiones nuevas: las del grupo de SocialHub pueden tener sentencias preparadas de antes del cambio de esquema
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(databases.jdbcUrl(AppMode.REAL), databases.user(), "");
    }

    private void sql(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) { st.execute(sql); }
    }

    private long number(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String text(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
