package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.auth.AuthService;
import com.socialhub.platform.AppPaths;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Arranca SocialHub entero (PostgreSQL incluido, migraciones, datos de ejemplo) y prueba la API. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static final String CSRF = "X-SocialHub-Request";
    private static final String PASSWORD = "contraseña-de-prueba";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired AuthService auth;
    @Autowired AppPaths paths;

    private Cookie session;

    @BeforeEach
    void login() throws Exception {
        if (auth.needsSetup()) {
            session = cookie(mvc.perform(post("/api/v1/auth/setup").header(CSRF, "1")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isNoContent()).andReturn());
        } else {
            session = cookie(mvc.perform(post("/api/v1/auth/login").header(CSRF, "1")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isNoContent()).andReturn());
        }
    }

    @Test
    @Order(1)
    void apiNeedsASessionAndTheProtectionHeader() throws Exception {
        mvc.perform(get("/api/v1/factory")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks/t1/actions/pause").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").header(CSRF, "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"otra\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/status")).andExpect(jsonPath("$.needsSetup").value(false))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @Order(2)
    void factorySnapshotComesFromTheDatabase() throws Exception {
        mvc.perform(get("/api/v1/factory").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.stations.length()").value(8))
                .andExpect(jsonPath("$.stations[0].id").value("inv"))
                .andExpect(jsonPath("$.tasks.length()").value(24))
                .andExpect(jsonPath("$.promotions.length()").value(2))
                .andExpect(jsonPath("$.brands[0].demo").value(true))
                .andExpect(jsonPath("$.alerts[?(@.id == 't13')].kind").value("err"))
                .andExpect(jsonPath("$.alerts[?(@.id == 'prog')].kind").value("block"))
                .andExpect(jsonPath("$.tasks[?(@.id == 't11')].actions[*].id").value(org.hamcrest.Matchers.hasItem("approve")));
        assertThat(jdbc.sql("select count(*) from publication where status = 'published' and confirmation is null")
                .query(Integer.class).single()).isZero();
    }

    @Test
    @Order(3)
    void actionsNotAllowedInTheCurrentStateAreRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/t3/actions/pause").cookie(session).header(CSRF, "1"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/tasks/nope/actions/pause").cookie(session).header(CSRF, "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tasks/t1/actions/fly").cookie(session).header(CSRF, "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void approvingTheReviewUnblocksSchedulingAndApprovesThePublications() throws Exception {
        assertThat(count("select count(*) from publication where promotion_id = 'delito-lanzamiento' and status = 'review'"))
                .isEqualTo(2);
        mvc.perform(post("/api/v1/tasks/t11/actions/approve").cookie(session).header(CSRF, "1"))
                .andExpect(status().isNoContent());
        assertThat(taskStatus("t11")).isEqualTo("ok");
        assertThat(taskStatus("t14")).isEqualTo("queued");
        assertThat(count("select count(*) from publication where promotion_id = 'delito-lanzamiento' and status = 'review'"))
                .isZero();
        assertThat(count("select count(*) from audit_event where action = 'task.approve'")).isEqualTo(1);
    }

    @Test
    @Order(5)
    void retryAndSwitchToManual() throws Exception {
        mvc.perform(post("/api/v1/tasks/t13/actions/retry").cookie(session).header(CSRF, "1"))
                .andExpect(status().isNoContent());
        assertThat(taskStatus("t13")).isEqualTo("queued");
        mvc.perform(post("/api/v1/tasks/t13/actions/retry").cookie(session).header(CSRF, "1"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(6)
    void pausingAStationPausesItsRunningTasks() throws Exception {
        mvc.perform(post("/api/v1/stations/gen/actions/pause").cookie(session).header(CSRF, "1"))
                .andExpect(status().isNoContent());
        assertThat(taskStatus("t7")).isEqualTo("wait");
        mvc.perform(post("/api/v1/stations/gen/actions/resume").cookie(session).header(CSRF, "1"))
                .andExpect(status().isNoContent());
        assertThat(taskStatus("t7")).isEqualTo("run");
    }

    @Test
    @Order(7)
    void changingTheAgentIsRecorded() throws Exception {
        mvc.perform(post("/api/v1/tasks/t1/actions/agent").cookie(session).header(CSRF, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"model\":\"Opus\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("select agent_name from task where id = 't1'").query(String.class).single()).isEqualTo("Opus");
        mvc.perform(get("/api/v1/promotions/delito-lanzamiento").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotion.name").value("Lanzamiento del poemario"));
    }

    @Test
    @Order(8)
    void resetFileForgetsThePassword() throws Exception {
        Files.writeString(paths.dataDir().resolve(AuthService.RESET_FILE), "");
        auth.resetIfRequested();
        assertThat(auth.needsSetup()).isTrue();
        assertThat(Files.exists(paths.dataDir().resolve(AuthService.RESET_FILE))).isFalse();
    }

    private String taskStatus(String id) {
        return jdbc.sql("select status from task where id = ?").param(id).query(String.class).single();
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private static Cookie cookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly").contains("SameSite=Strict");
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Cookie("SOCIALHUB_SESSION", value);
    }
}
