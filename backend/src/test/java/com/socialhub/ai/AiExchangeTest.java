package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Intercambio con Claude de extremo a extremo, en modo real: encargos a «para-claude», resultados de «de-claude»,
 * límite diario, contrato y subida de modelo en el tercer intento. Hace de Claude escribiendo los resultados a mano.
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiExchangeTest {

    @TempDir static Path drive;
    static Path orders;
    static String research1;
    static String research2;
    static String research3;

    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void folders() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
    }

    @Test
    @Order(1)
    void defaultsAndSetup() throws Exception {
        mvc.perform(get("/api/ai/settings"))
                .andExpect(jsonPath("$.daily_limit").value(10))
                .andExpect(jsonPath("$.used_today").value(0))
                .andExpect(jsonPath("$.models[?(@.type == 'WEEKLY_BALANCE')].model").value("OPUS"))
                .andExpect(jsonPath("$.models[?(@.type == 'RESEARCH')].model").value("SONNET"));
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE",
                 "rules":["Nunca enviar correos"]}""").andExpect(status().isCreated());
        write(put("/api/ai/settings"), """
                {"daily_limit":2,"models":[{"type":"RESEARCH","model":"SONNET"}]}""")
                .andExpect(jsonPath("$.daily_limit").value(2));
        write(put("/api/ai/settings"), "{\"daily_limit\":500,\"models\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    void withoutAnOrdersFolderTheOrderWaitsWithTheReason() throws Exception {
        research1 = order("Salas de poesía en Granada");
        waitFor(() -> task(research1).path("reason").asString("").contains("no tiene carpeta de «Encargos»"),
                "motivo: falta la carpeta de encargos");
        assertThat(task(research1).get("status").asString()).isEqualTo("QUEUED");
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"ORDERS\",\"location\":"
                + json.writeValueAsString(orders.toString()) + "}]}").andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void ordersGoOutUntilTheDailyLimit() throws Exception {
        research2 = order("Revistas que aceptan poesía");
        research3 = order("Clubes de lectura");
        waitFor(() -> "WAITING".equals(task(research2).get("status").asString()), "segundo encargo enviado");
        waitFor(() -> task(research3).path("reason").asString("").contains("Límite diario"), "tercero espera al límite");
        assertThat(task(research1).get("status").asString()).isEqualTo("WAITING");
        assertThat(task(research1).get("agent").get("name").asString()).isEqualTo("Sonnet");
        assertThat(task(research3).get("status").asString()).isEqualTo("QUEUED");

        List<Path> sent = files(orders.resolve("para-claude"));
        assertThat(sent).extracting(p -> p.getFileName().toString())
                .containsExactlyInAnyOrder(research1 + "-1.json", research2 + "-1.json");
        JsonNode request = json.readTree(Files.readString(orders.resolve("para-claude/" + research1 + "-1.json")));
        assertThat(request.get("contract").asString()).isEqualTo("socialhub.task-request");
        assertThat(request.get("type").asString()).isEqualTo("socialhub.research");
        assertThat(request.get("model").asString()).isEqualTo("sonnet");
        assertThat(request.get("brand").asString()).isEqualTo("el-delito");
        assertThat(request.get("rules").get(0).asString()).isEqualTo("Nunca enviar correos");
        assertThat(request.get("inputs").get("items").get(0).asString()).isEqualTo("Granada");
        assertThat(Files.isDirectory(orders.resolve("de-claude"))).isTrue();
        mvc.perform(get("/api/ai/settings")).andExpect(jsonPath("$.used_today").value(2));
    }

    @Test
    @Order(4)
    void aCompletedResultCompletesTheTask() throws Exception {
        result(research1, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed",
                 "outputs":{"summary":"5 salas encontradas","report":"# Salas"},"warnings":["Una sala cierra en diciembre"],
                 "agent":{"runtime":"tarea programada en la nube","model":"sonnet"},
                 "startedAt":"2026-11-02T09:40:00Z","finishedAt":"2026-11-02T09:43:00Z"}""".formatted(research1));
        waitFor(() -> "COMPLETED".equals(task(research1).get("status").asString()), "primer encargo completado");
        // La marca no tiene carpeta de Informes: el informe se queda en el resultado de Claude
        assertThat(task(research1).get("result").asString()).startsWith("5 salas encontradas · La promoción no tiene carpeta")
                .endsWith(" · Avisos: Una sala cierra en diciembre");
        JsonNode page = json.readTree(mvc.perform(get("/api/ai/requests")).andReturn().getResponse().getContentAsString());
        JsonNode first = find(page, research1);
        assertThat(first.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(first.get("duration_ms").asLong()).isEqualTo(180_000);
        assertThat(first.get("runtime").asString()).isEqualTo("tarea programada en la nube");
    }

    @Test
    @Order(5)
    void anInvalidResultFailsTheTaskAndRetriesEscalateOnTheThirdAttempt() throws Exception {
        // Sin «agent»: no cumple el contrato
        result(research2, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed","outputs":{}}"""
                .formatted(research2));
        waitFor(() -> "FAILED".equals(task(research2).get("status").asString()), "resultado no válido");
        assertThat(task(research2).get("error").asString()).contains("Resultado no válido").contains("agent");

        write(put("/api/ai/settings"), "{\"daily_limit\":10,\"models\":[]}").andExpect(status().isOk());
        command(research2, "RETRY");
        waitFor(() -> "WAITING".equals(task(research2).get("status").asString()), "segundo intento enviado");
        waitFor(() -> "WAITING".equals(task(research3).get("status").asString()), "tercero sale al subir el límite");
        assertThat(Files.exists(orders.resolve("para-claude/" + research2 + "-2.json"))).isTrue();
        assertThat(task(research2).get("agent").get("name").asString()).isEqualTo("Sonnet");

        result(research2, failed(research2, "No encuentro la web de la revista"));
        waitFor(() -> "FAILED".equals(task(research2).get("status").asString()), "segundo intento falla");
        assertThat(task(research2).get("error").asString()).isEqualTo("No encuentro la web de la revista");
        command(research2, "RETRY");
        waitFor(() -> "WAITING".equals(task(research2).get("status").asString()), "tercer intento enviado");
        assertThat(task(research2).get("agent").get("name").asString()).isEqualTo("Opus");
        JsonNode third = json.readTree(Files.readString(orders.resolve("para-claude/" + research2 + "-3.json")));
        assertThat(third.get("model").asString()).isEqualTo("opus");
        assertThat(task(research2).get("attempts").asInt()).isEqualTo(3);
        assertThat(task(research2).get("max_attempts").asInt()).isEqualTo(3);
    }

    @Test
    @Order(6)
    void needsHumanAndUnknownResults() throws Exception {
        result(research3, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"needs_human","outputs":{},
                 "error":"¿Incluyo clubes de fuera de Granada?","agent":{"runtime":"tarea programada en la nube"}}"""
                .formatted(research3));
        waitFor(() -> task(research3).get("human").asBoolean(), "Claude pide a Raúl");
        assertThat(task(research3).get("status").asString()).isEqualTo("WAITING");
        assertThat(task(research3).get("reason").asString()).isEqualTo("¿Incluyo clubes de fuera de Granada?");

        // Un resultado repetido o de una tarea que no lo espera no cambia nada
        result("00000000-0000-0000-0000-000000000000", failed("00000000-0000-0000-0000-000000000000", "x"));
        Thread.sleep(1000);
        assertThat(task(research1).get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(7)
    void whenTheOrdersFolderIsMissingTheOrderWaitsBlocked() throws Exception {
        Path away = drive.resolve("DeLito/encargos-fuera");
        Files.move(orders, away);
        String id = order("Festivales");
        waitFor(() -> "BLOCKED".equals(task(id).get("status").asString()), "bloqueado sin carpeta");
        assertThat(task(id).get("waiting_for").asString()).isEqualTo(orders.toString());
        Files.move(away, orders);
        mvc.perform(post("/api/tasks/{id}/commands", id).header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"RECHECK\"}")).andExpect(status().isOk());
        waitFor(() -> "WAITING".equals(task(id).get("status").asString()), "sale al volver la carpeta");
    }

    // ---------------------------------------------------------------- utilidades

    private String order(String goal) throws Exception {
        String body = write(post("/api/promotions/poemario/ai-orders"), """
                {"type":"RESEARCH","title":"Investigación","goal":"%s","inputs":["Granada"]}""".formatted(goal))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("task_id").asString();
    }

    private void result(String taskId, String content) throws Exception {
        Path dir = Files.createDirectories(orders.resolve("de-claude"));
        Path file = dir.resolve(taskId + "-" + System.nanoTime() + ".json");
        Files.writeString(file, content);
        // Como si llevara un rato sincronizado
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private static String failed(String taskId, String error) {
        return """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"failed","outputs":{},
                 "error":"%s","agent":{"runtime":"tarea programada en la nube"}}""".formatted(taskId, error);
    }

    private void command(String taskId, String type) throws Exception {
        mvc.perform(post("/api/tasks/{id}/commands", taskId).header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\"}")).andExpect(status().isOk());
    }

    private JsonNode task(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/{id}", id)).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode page, String taskId) {
        for (JsonNode item : page.get("items")) if (item.get("task_id").asString().equals(taskId)) return item;
        throw new AssertionError("Sin envío para " + taskId);
    }

    private static List<Path> files(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
    }

    private void waitFor(Callable<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.call()) {
            assertThat(System.currentTimeMillis()).as(what).isLessThan(deadline);
            Thread.sleep(150);
        }
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
