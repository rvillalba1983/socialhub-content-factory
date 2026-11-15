package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Calendario externo» (manual, §4), de extremo a extremo y con el calendario en marcha por su cuenta: Publora se
 * lee por su API cada hora (servidor HTTP local) y Publer y la red, con el encargo diario «Leer calendario» a Claude
 * (archivo de resultado). Se prueba lo que no cubría el camino feliz: el fallo de Publora conserva lo anterior y dice
 * el error, el resultado no válido de Claude, el reemplazo de lo antiguo, el encargo solo una vez al día y que lo de
 * fuera llegue al encargo de propuestas de la semana ({@code planned_outside_socialhub}).
 */
@FlowTest
@TestPropertySource(properties = {
        "socialhub.calendar.initial-delay-millis=200",
        "socialhub.calendar.tick-millis=300"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalCalendarFlowTest {

    static final String KEY = "sk_123.abcdef0123456789";

    @TempDir static Path drive;
    static Path orders;
    static HttpServer publora;
    /** A: lo hecho a mano (g-9), una cuenta ajena y un borrador · FAIL: error del servidor · B: otra cosa distinta (g-10). */
    static volatile String mode = "A";

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        publora = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        publora.createContext("/", ExternalCalendarFlowTest::handle);
        publora.start();
        registry.add("socialhub.publora.base-url", () -> "http://127.0.0.1:" + publora.getAddress().getPort() + "/api/v1");
    }

    @AfterAll
    static void stop() { publora.stop(0); }

    static Instant madrid(int day, int hour) {
        return LocalDateTime.of(2026, 11, day, hour, 0).atZone(FlowClock.MADRID).toInstant();
    }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        ex.getRequestBody().readAllBytes();
        String reply;
        int code = 200;
        if (!KEY.equals(ex.getRequestHeaders().getFirst("x-publora-key"))) {
            code = 401;
            reply = "{\"error\":\"Invalid API key\"}";
        } else if (path.equals("/api/v1/platform-connections")) {
            reply = """
                    {"success":true,"connections":[
                      {"platformId":"instagram-111","username":"elde_lito","displayName":"El DeLito","connectionStatus":"active"},
                      {"platformId":"tiktok-222","username":"litoques","displayName":"Litoques","connectionStatus":"active"}]}""";
        } else if (path.equals("/api/v1/list-posts")) {
            if ("FAIL".equals(mode)) {
                code = 503;
                reply = "{\"error\":\"Servicio no disponible\",\"code\":\"UNAVAILABLE\"}";
            } else if ("A".equals(mode)) {
                reply = """
                        {"success":true,"posts":[
                          {"postGroupId":"g-9","content":"Hecha a mano en Publora (A)","status":"scheduled","scheduledTime":"%s",
                           "platforms":[{"platformId":"instagram-111","platform":"instagram","status":"scheduled"}]},
                          {"postGroupId":"g-8","content":"Otra cuenta","status":"scheduled","scheduledTime":"%s",
                           "platforms":[{"platformId":"tiktok-222","platform":"tiktok","status":"scheduled"}]},
                          {"postGroupId":"g-7","content":"Borrador","status":"draft","scheduledTime":"%s",
                           "platforms":[{"platformId":"instagram-111","platform":"instagram","status":"draft"}]}]}"""
                        .formatted(madrid(13, 9), madrid(13, 10), madrid(13, 11));
            } else {
                reply = """
                        {"success":true,"posts":[
                          {"postGroupId":"g-10","content":"Hecha a mano en Publora (B)","status":"scheduled","scheduledTime":"%s",
                           "platforms":[{"platformId":"instagram-111","platform":"instagram","status":"scheduled"}]}]}"""
                        .formatted(madrid(18, 10));
            }
        } else {
            reply = "{\"success\":true}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/publora/key", "{\"key\":\"" + KEY + "\"}"));
        f.ok(f.put("/api/brands/el-delito/publora-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"instagram-111\"}]}"));
    }

    @Test
    @Order(2)
    void publoraIsReadByItsApiAndOnlyTheBrandsAccountsAndNoDraftsAppear() throws Exception {
        Flow f = flow();
        f.waitFor(() -> calendar().get("items").size() == 1, "Publora leída");
        JsonNode item = calendar().get("items").get(0);
        assertThat(item.get("source").asString()).isEqualTo("PUBLORA");
        assertThat(item.get("external_id").asString()).isEqualTo("g-9");
        assertThat(item.get("text").asString()).isEqualTo("Hecha a mano en Publora (A)");
        assertThat(item.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(item.hasNonNull("publication_id")).isFalse();
        assertThat(source("PUBLORA").hasNonNull("synced_at")).isTrue();
        assertThat(source("PUBLORA").hasNonNull("error")).isFalse();
    }

    @Test
    @Order(3)
    void theDailyOrderGoesOutOnlyOnceADayAndAnInvalidResultOfClaudeIsAFailureThatChangesNothing() throws Exception {
        Flow f = flow();
        f.waitFor(() -> !f.requestsOfType("para-claude-pc", "socialhub.calendar-read").isEmpty(), "encargo diario enviado");
        Thread.sleep(1_000); // varias vueltas del calendario después: sigue siendo uno
        List<JsonNode> sent = f.requestsOfType("para-claude-pc", "socialhub.calendar-read");
        assertThat(sent).hasSize(1);
        JsonNode request = sent.getFirst();
        assertThat(request.get("inputs").get("brand").get("handle").asString()).isEqualTo("@elde_lito");
        String task = request.get("taskId").asString();
        assertThat(f.task(task).get("status").asString()).isEqualTo("WAITING");

        // Un elemento sin origen válido: el resultado entero se rechaza y no se guarda nada de Publer ni de la red
        f.result(task, Flow.completed(task, """
                {"items":[{"source":"publer","platform":"INSTAGRAM","at":"%s","status":"scheduled","text":"Bien"},
                          {"source":"otra-cosa","platform":"INSTAGRAM","at":"%s","status":"scheduled"}]}"""
                .formatted(madrid(19, 19), madrid(19, 20))));
        f.waitFor(() -> "FAILED".equals(f.task(task).get("status").asString()), "resultado rechazado");
        assertThat(f.task(task).get("error").asString()).contains("Elemento 2").contains("source");
        assertThat(source("PUBLER").get("error").asString()).contains("Elemento 2");
        assertThat(calendar().get("items")).hasSize(1); // solo lo de Publora
    }

    @Test
    @Order(4)
    void aPubloraErrorKeepsWhatWasReadBeforeAndSaysWhy() throws Exception {
        Flow f = flow();
        String before = source("PUBLORA").get("synced_at").asString();
        mode = "FAIL";
        clock.forward(Duration.ofMinutes(61));
        f.waitFor(() -> source("PUBLORA").hasNonNull("error"), "error de Publora anotado");
        assertThat(source("PUBLORA").get("error").asString()).contains("Servicio no disponible");
        // Lo leído antes sigue ahí, con la fecha de la última lectura buena
        assertThat(calendar().get("items")).hasSize(1);
        assertThat(calendar().get("items").get(0).get("external_id").asString()).isEqualTo("g-9");
        assertThat(source("PUBLORA").get("synced_at").asString()).isEqualTo(before);
    }

    @Test
    @Order(5)
    void whenPubloraIsBackTheOldReadingIsReplacedNotAdded() throws Exception {
        Flow f = flow();
        mode = "B";
        clock.forward(Duration.ofMinutes(61));
        f.waitFor(() -> calendar().get("items").size() == 1 && "g-10".equals(calendar().get("items").get(0).get("external_id").asString()),
                "lectura nueva de Publora");
        assertThat(source("PUBLORA").hasNonNull("error")).isFalse();
        assertThat(source("PUBLORA").hasNonNull("synced_at")).isTrue();
    }

    @Test
    @Order(6)
    void thenextDayClaudeReadsPublerAndTheNetworkAndEverythingOutsideReachesTheWeeklyProposalsOrder() throws Exception {
        Flow f = flow();
        // Al día siguiente sale un encargo nuevo
        clock.set(madrid(11, 10));
        f.waitFor(() -> f.requestsOfType("para-claude-pc", "socialhub.calendar-read").size() == 2, "encargo del día siguiente");
        JsonNode second = f.requestsOfType("para-claude-pc", "socialhub.calendar-read").stream()
                .filter(r -> r.get("inputs").get("publer_window").toString().contains("2026-11-11")).findFirst().orElseThrow();
        String task = second.get("taskId").asString();
        // Algo ya planificado en SocialHub esa semana: no cuenta como «de fuera»
        f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Reel","title":"Ya planificado","scheduled_on":"2026-11-17","scheduled_time":"19:00",
                 "initial_status":"IN_PRODUCTION"}"""));

        f.result(task, Flow.completed(task, """
                {"items":[
                   {"source":"publer","platform":"INSTAGRAM","at":"%s","status":"scheduled","text":"Reel del jueves"},
                   {"source":"network","platform":"TIKTOK","at":"%s","status":"published","url":"https://www.tiktok.com/@litoques/video/1"}],
                 "summary":"2 encontradas"}""".formatted(madrid(19, 19), madrid(9, 12))));
        f.waitFor(() -> "COMPLETED".equals(f.task(task).get("status").asString()), "calendario leído");
        assertThat(f.task(task).get("result").asString()).startsWith("Calendario leído: 1 en Publer y 1 publicadas en la red");
        f.waitFor(() -> calendar().get("items").size() == 3, "las tres fuentes en el calendario");
        List<String> sources = new ArrayList<>();
        calendar().get("items").forEach(i -> sources.add(i.get("source").asString()));
        assertThat(sources).containsExactlyInAnyOrder("PUBLORA", "PUBLER", "NETWORK");
        assertThat(source("PUBLER").hasNonNull("error")).isFalse();
        assertThat(source("PUBLER").hasNonNull("synced_at")).isTrue();
        assertThat(source("NETWORK").hasNonNull("synced_at")).isTrue();

        // El encargo de propuestas de la semana del 16 recibe lo de fuera de esa semana (Publora el 18, Publer el 19)
        String proposals = f.order("poemario", "WEEKLY_PROPOSALS", "Propón la semana", "2026-11-16");
        JsonNode inputs = f.request(proposals).get("inputs");
        assertThat(inputs.get("already_planned").get(0).get("title").asString()).isEqualTo("Ya planificado");
        JsonNode outside = inputs.get("planned_outside_socialhub");
        assertThat(outside).hasSize(2);
        List<String> texts = new ArrayList<>();
        outside.forEach(o -> texts.add(o.get("source").asString() + ": " + o.get("text").asString()));
        assertThat(texts).containsExactly("Publora: Hecha a mano en Publora (B)", "Publer: Reel del jueves");
        assertThat(outside.get(0).get("at").asString()).isEqualTo("2026-11-18T10:00");
        assertThat(outside.toString()).doesNotContain("Ya planificado").doesNotContain("tiktok.com");
    }

    // ---------------------------------------------------------------- utilidades

    private JsonNode calendar() throws Exception {
        return flow().read("/api/promotions/poemario/external-calendar?from=2026-11-01&to=2026-11-30");
    }

    private JsonNode source(String id) throws Exception {
        for (JsonNode s : calendar().get("sources")) {
            if (s.get("source").asString().equals(id)) return s;
        }
        throw new AssertionError("No hay fuente " + id + ": " + calendar().get("sources"));
    }
}
