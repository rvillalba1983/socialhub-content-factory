package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;

/** Arranca SocialHub entero (PostgreSQL incluido, migraciones, datos de ejemplo) y prueba la API. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.operations.flush-millis=200"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static final String GUARD = "X-SocialHub-Request";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    @Order(1)
    void onlyLocalRequestsAndMutationsWithTheGuardHeaderAreAccepted() throws Exception {
        mvc.perform(get("/api/factory").header("Host", "socialhub.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/factory").header("Host", "127.0.0.1:8686")).andExpect(status().isOk());
        command("t1", "{\"type\":\"PAUSE\"}", false).andExpect(status().isForbidden());
        assertThat(taskStatus("t1")).isEqualTo("RUNNING");
    }

    @Test
    @Order(2)
    void factorySnapshotComesFromTheDatabase() throws Exception {
        mvc.perform(get("/api/factory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.stations.length()").value(8))
                .andExpect(jsonPath("$.stations[0].id").value("inv"))
                .andExpect(jsonPath("$.stations[0].counts.total").value(3))
                .andExpect(jsonPath("$.tasks.length()").value(24))
                .andExpect(jsonPath("$.promotions.length()").value(2))
                .andExpect(jsonPath("$.promotions[0].milestones").isNotEmpty())
                .andExpect(jsonPath("$.brands[0].demo").value(true))
                .andExpect(jsonPath("$.common_rules.length()").value(5))
                .andExpect(jsonPath("$.alerts[?(@.target_id == 't13')].kind").value("FAILED"))
                .andExpect(jsonPath("$.alerts[?(@.target_id == 'prog')].kind").value("BLOCKED"))
                .andExpect(jsonPath("$.tasks[?(@.id == 't11')].available_commands[*].type").value(hasItem("APPROVE")))
                .andExpect(jsonPath("$.armory_items").isNotEmpty());
        assertThat(count("select count(*) from publication where status = 'PUBLISHED' and confirmation is null")).isZero();
    }

    @Test
    @Order(3)
    void tasksCanBeFilteredAndRead() throws Exception {
        mvc.perform(get("/api/tasks").param("station_id", "prog").param("status", "BLOCKED,FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(get("/api/tasks/t13"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.agent.kind").value("BROWSER"))
                .andExpect(jsonPath("$.max_attempts").value(2));
        mvc.perform(get("/api/tasks/nope")).andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void commandsNotAllowedInTheCurrentStateAreRejected() throws Exception {
        command("t3", "{\"type\":\"PAUSE\"}", true).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        command("nope", "{\"type\":\"PAUSE\"}", true).andExpect(status().isNotFound());
        command("t1", "{\"type\":\"FLY\"}", true).andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void approvingTheReviewUnblocksSchedulingAndApprovesThePublications() throws Exception {
        String inReview = "select count(*) from publication where promotion_id = 'delito-lanzamiento' and status = 'IN_REVIEW'";
        assertThat(count(inReview)).isEqualTo(2);
        command("t11", "{\"type\":\"APPROVE\"}", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(taskStatus("t14")).isEqualTo("QUEUED");
        assertThat(count(inReview)).isZero();
        // Auditoría de Hibernate Envers: el cambio de estado queda registrado
        assertThat(count("select count(*) from task_aud where id = 't11' and status = 'COMPLETED'")).isEqualTo(1);
        assertThat(count("select count(*) from publication_aud where status = 'APPROVED'")).isEqualTo(2);
        assertThat(count("select count(*) from task_event where task_id = 't11' and message = 'Aprobada por Raúl'"))
                .isEqualTo(1);
    }

    @Test
    @Order(6)
    void retriesAreLimited() throws Exception {
        command("t13", "{\"type\":\"RETRY\"}", true).andExpect(status().isOk());
        assertThat(taskStatus("t13")).isEqualTo("QUEUED");
        command("t13", "{\"type\":\"RETRY\"}", true).andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    void pausingAStationPausesItsRunningTasks() throws Exception {
        mvc.perform(post("/api/stations/gen/commands").header(GUARD, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PAUSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.available_commands[0]").value("RESUME"));
        assertThat(taskStatus("t7")).isEqualTo("WAITING");
        mvc.perform(post("/api/stations/gen/commands").header(GUARD, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"RESUME\"}"))
                .andExpect(status().isOk());
        assertThat(taskStatus("t7")).isEqualTo("RUNNING");
        assertThat(count("select count(*) from station_aud where id = 'gen'")).isEqualTo(2);
    }

    @Test
    @Order(8)
    void changingTheAgentNeedsAModel() throws Exception {
        command("t1", "{\"type\":\"CHANGE_AGENT\",\"model\":\"OPUS\"}", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent.name").value("Opus"));
        command("t1", "{\"type\":\"CHANGE_AGENT\"}", true).andExpect(status().isBadRequest());
        mvc.perform(get("/api/promotions/delito-lanzamiento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lanzamiento del poemario"))
                .andExpect(jsonPath("$.automations[0].model").exists());
        mvc.perform(get("/api/promotions/nope")).andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void publicationsArePagedAndFiltered() throws Exception {
        mvc.perform(get("/api/promotions/litoques-lanzamiento/publications").param("limit", "5").param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.offset").value(2))
                .andExpect(jsonPath("$.total").value(31))
                .andExpect(jsonPath("$.items[0].scheduled_time").value("19:00"));
        mvc.perform(get("/api/promotions/litoques-lanzamiento/publications").param("flagged", "true"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/promotions/delito-lanzamiento/publications").param("platform", "TIKTOK"))
                .andExpect(jsonPath("$.items[0].platform").value("TIKTOK"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/promotions/delito-lanzamiento/publications").param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    void externalOperationsAreRecorded() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (count("select count(*) from external_operation where system = 'postgres' and operation = 'start'") == 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        mvc.perform(get("/api/external-operations").param("system", "postgres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].operation").value(hasItem("start")))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"));
        mvc.perform(get("/api/external-operations").param("outcome", "FAILURE"))
                .andExpect(jsonPath("$.total").value(0));
    }

    private ResultActions command(String taskId, String body, boolean withGuard) throws Exception {
        var request = post("/api/tasks/{id}/commands", taskId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (withGuard) request.header(GUARD, "1");
        return mvc.perform(request);
    }

    private String taskStatus(String id) {
        return jdbc.sql("select status from task where id = ?").param(id).query(String.class).single();
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
