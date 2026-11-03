package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Autonomía (fase 8): un encargo fallido se reintenta solo, pasada media hora, hasta el tercer intento (que sube de modelo). */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
class AiAutoRetryTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final Clock BASE = Clock.system(MADRID);
    static volatile Duration offset = Duration.ZERO;

    @TempDir static Path drive;

    @Autowired MockMvc mvc;
    @Autowired AiAutoRetry retry;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() {
            return new Clock() {
                @Override public ZoneId getZone() { return MADRID; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return BASE.instant().plus(offset); }
            };
        }
    }

    @Test
    void failedOrdersAreRetriedUpToTheThirdAttempt() throws Exception {
        Path orders = Files.createDirectories(drive.resolve("encargos"));
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(LocalDate.now(MADRID), LocalDate.now(MADRID).plusDays(60))).andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + q(orders) + "}]}")
                .andExpect(status().isOk());
        write(put("/api/ai/settings"), "{\"daily_limit\":100,\"models\":[]}").andExpect(status().isOk());
        String body = write(post("/api/promotions/poemario/ai-orders"), "{\"type\":\"RESEARCH\",\"goal\":\"Salas\",\"inputs\":[\"Granada\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("task_id").asString();

        for (int attempt = 1; attempt <= 3; attempt++) {
            int n = attempt;
            waitFor(() -> "WAITING".equals(task(id).get("status").asString()) && task(id).get("attempts").asInt() == n, "intento " + n + " enviado");
            assertThat(request(orders, id, attempt).get("model").asString()).isEqualTo(attempt == 3 ? "opus" : "sonnet");
            fail(orders, id, attempt);
            waitFor(() -> "FAILED".equals(task(id).get("status").asString()), "intento " + n + " fallido");
            // Recién fallado no se toca; pasada media hora, sí (mientras queden intentos)
            assertThat(retry.retryDue()).isZero();
            offset = offset.plus(AiAutoRetry.WAIT).plusMinutes(1);
            assertThat(retry.retryDue()).isEqualTo(attempt < 3 ? 1 : 0);
        }
        assertThat(task(id).get("status").asString()).isEqualTo("FAILED");
        assertThat(task(id).get("events").toString()).contains("Reintentado por SocialHub sin esperar a Raúl");
    }

    private JsonNode request(Path orders, String id, int attempt) throws Exception {
        return json.readTree(Files.readString(orders.resolve("para-claude").resolve(id + "-" + attempt + ".json")));
    }

    private void fail(Path orders, String id, int attempt) throws Exception {
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(id + "-" + attempt + ".json");
        Files.writeString(file, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"failed","outputs":{},
                 "error":"Sin acceso a la web","agent":{"runtime":"tarea programada en la nube"}}""".formatted(id));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private JsonNode task(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/{id}", id)).andReturn().getResponse().getContentAsString());
    }

    private String q(Object text) throws Exception { return json.writeValueAsString(text.toString()); }

    private void waitFor(Callable<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.call()) {
            assertThat(System.currentTimeMillis()).as(what).isLessThan(deadline);
            Thread.sleep(150);
        }
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @SuppressWarnings("unused")
    private static Stream<Path> unused() { return Stream.empty(); }
}
