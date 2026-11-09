package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vía Publora contra un Publora de mentira (servidor HTTP local): clave, cuentas de la marca, programar con subida de
 * archivos, comprobar, quitarla al desprogramar y errores de Publora.
 */
@SpringBootTest(properties = {
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.engine.poll-millis=200",
        "socialhub.engine.initial-delay-millis=200",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PubloraRouteTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final String GOOD_KEY = "sk_123.abcdef0123456789";
    static final PublishingEngineTest.MovableClock CLOCK = new PublishingEngineTest.MovableClock();
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(10);

    @TempDir static Path drive;
    static Path dataDir;
    static Path approved;
    static Path orders;
    static Path story;
    static HttpServer publora;
    /** Lo que ha recibido el Publora de mentira: «MÉTODO ruta» y cuerpo. */
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final Map<String, String> bodies = new ConcurrentHashMap<>();
    static volatile String postStatus = "scheduled";
    static volatile boolean limitReached = false;

    @Autowired MockMvc mvc;
    @Autowired PublishingEngine engine;
    @Autowired ExternalCalendar external;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() { return CLOCK; }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "it-publora");
        registry.add("socialhub.data-dir", dataDir::toString);
        publora = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        publora.createContext("/", PubloraRouteTest::handle);
        publora.start();
        registry.add("socialhub.publora.base-url", () -> "http://127.0.0.1:" + publora.getAddress().getPort() + "/api/v1");
    }

    @AfterAll
    static void stop() { publora.stop(0); }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String call = ex.getRequestMethod() + " " + path;
        calls.add(call);
        bodies.put(call, body);
        String reply;
        int code = 200;
        if (path.startsWith("/upload/")) {
            reply = "";
        } else if (!GOOD_KEY.equals(ex.getRequestHeaders().getFirst("x-publora-key"))) {
            code = 401;
            reply = "{\"error\":\"Invalid API key\"}";
        } else if (path.equals("/api/v1/platform-connections")) {
            reply = """
                    {"success":true,"connections":[
                      {"platformId":"instagram-111","username":"elde_lito","displayName":"El DeLito","connectionStatus":"active"},
                      {"platformId":"tiktok-222","username":"litoques","displayName":"Litoques","connectionStatus":"active"}]}""";
        } else if (path.equals("/api/v1/create-post")) {
            if (limitReached) {
                code = 403;
                reply = "{\"error\":\"Monthly post limit reached\",\"code\":\"POST_LIMIT_REACHED\"}";
            } else {
                reply = "{\"success\":true,\"postGroupId\":\"g-1\",\"scheduledTime\":null}";
            }
        } else if (path.equals("/api/v1/get-upload-url")) {
            reply = "{\"success\":true,\"uploadUrl\":\"http://127.0.0.1:" + publora.getAddress().getPort()
                    + "/upload/m-1\",\"mediaId\":\"m-1\"}";
        } else if (path.equals("/api/v1/list-posts")) {
            Instant base = LocalDateTime.of(DAY, java.time.LocalTime.of(9, 0)).atZone(MADRID).toInstant();
            reply = """
                    {"success":true,"posts":[
                      {"postGroupId":"g-1","content":"De SocialHub","status":"published","scheduledTime":"%s",
                       "platforms":[{"platformId":"111","platform":"instagram","status":"published"}]},
                      {"postGroupId":"g-9","content":"Hecha a mano en Publora","status":"scheduled","scheduledTime":"%s",
                       "platforms":[{"platformId":"instagram-111","platform":"instagram","status":"scheduled"}]},
                      {"postGroupId":"g-8","content":"Otra cuenta","status":"scheduled","scheduledTime":"%s",
                       "platforms":[{"platformId":"tiktok-222","platform":"tiktok","status":"scheduled"}]},
                      {"postGroupId":"g-7","content":"Borrador","status":"draft","scheduledTime":"%s",
                       "platforms":[{"platformId":"instagram-111","platform":"instagram","status":"draft"}]}]}"""
                    .formatted(base, base.plusSeconds(86400), base.plusSeconds(3600), base.plusSeconds(7200));
        } else if (path.startsWith("/api/v1/get-post/")) {
            reply = "published".equals(postStatus)
                    ? "{\"success\":true,\"status\":\"published\",\"posts\":[{\"platform\":\"instagram\",\"status\":\"published\","
                    + "\"postedId\":\"1789\",\"permalink\":\"https://www.instagram.com/stories/elde_lito/1789\"}]}"
                    : "{\"success\":true,\"status\":\"scheduled\",\"posts\":[{\"platform\":\"instagram\",\"status\":\"scheduled\"}]}";
        } else {
            reply = "{\"success\":true}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) ex.getResponseBody().write(bytes);
        ex.close();
    }

    @BeforeAll
    static void folders() throws Exception {
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        BufferedImage image = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1080, 1920);
        g.dispose();
        story = approved.resolve("historia.jpg");
        ImageIO.write(image, "jpg", story.toFile());
    }

    @Test
    @Order(1)
    void keyIsCheckedAndKeptInALocalFile() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(LocalDate.now(MADRID), DAY.plusDays(30))).andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"APPROVED\",\"location\":" + q(approved)
                + "},{\"role\":\"ORDERS\",\"location\":" + q(orders) + "}]}")
                .andExpect(status().isOk());

        assertThat(get("/api/publora").get("configured").asBoolean()).isFalse();
        // Sin clave no se eligen cuentas
        write(put("/api/brands/el-delito/publora-accounts"), "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"instagram-111\"}]}")
                .andExpect(status().isBadRequest());

        write(put("/api/publora/key"), "{\"key\":\"sk_999.bad0000000000\"}").andExpect(status().isBadRequest());
        assertThat(get("/api/publora").get("configured").asBoolean()).isFalse();

        JsonNode saved = json.readTree(write(put("/api/publora/key"), "{\"key\":\"" + GOOD_KEY + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.get("key_hint").asString()).isEqualTo("…6789");
        assertThat(saved.get("connections").size()).isEqualTo(2);
        assertThat(saved.toString()).doesNotContain(GOOD_KEY);

        Path file = dataDir.resolve("secretos/publora.key");
        assertThat(Files.readString(file)).isEqualTo(GOOD_KEY);
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
        }
    }

    @Test
    @Order(2)
    void eachBrandChoosesItsAccounts() throws Exception {
        write(put("/api/brands/el-delito/publora-accounts"), "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"tiktok-222\"}]}")
                .andExpect(status().isBadRequest());
        write(put("/api/brands/el-delito/publora-accounts"), "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"instagram-9\"}]}")
                .andExpect(status().isBadRequest());
        write(put("/api/brands/el-delito/publora-accounts"), "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"instagram-111\"}]}")
                .andExpect(status().isOk());
        JsonNode list = get("/api/brands/el-delito/publora-accounts").get("items");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("label").asString()).isEqualTo("instagram · elde_lito");
    }

    @Test
    @Order(2)
    void notUsingPubloraCanBeSetAndUndoneWithoutTouchingTheAccounts() throws Exception {
        assertThat(get("/api/brands/el-delito/publora-accounts").get("dismissed").asBoolean()).isFalse();
        int accountsBefore = get("/api/brands/el-delito/publora-accounts").get("items").size();
        JsonNode on = json.readTree(write(put("/api/brands/el-delito/publora-dismissed"), "{\"dismissed\":true}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(on.get("dismissed").asBoolean()).isTrue();
        assertThat(get("/api/brands/el-delito/publora-accounts").get("dismissed").asBoolean()).isTrue();
        // Marcarlo dos veces no falla y las cuentas siguen
        write(put("/api/brands/el-delito/publora-dismissed"), "{\"dismissed\":true}").andExpect(status().isOk());
        assertThat(get("/api/brands/el-delito/publora-accounts").get("items").size()).isEqualTo(accountsBefore);
        // Reactivar
        JsonNode off = json.readTree(write(put("/api/brands/el-delito/publora-dismissed"), "{\"dismissed\":false}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(off.get("dismissed").asBoolean()).isFalse();
        assertThat(get("/api/brands/el-delito/publora-accounts").get("dismissed").asBoolean()).isFalse();
        write(put("/api/brands/no-existe/publora-dismissed"), "{\"dismissed\":true}").andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    void schedulesWithFilesAndConfirmsWithTheLink() throws Exception {
        // A 7 h: Publer ya no llega (necesita 8 h) y se va a Publora
        LocalDateTime when = LocalDateTime.of(DAY, java.time.LocalTime.of(9, 0));
        Instant at = when.atZone(MADRID).toInstant();
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = approvedStory("09:00");
        calls.clear();
        schedule(id);

        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(job.get("route").asString()).isEqualTo("Publora");
        assertThat(job.get("external_id").asString()).isEqualTo("g-1");
        assertThat(job.get("log").toString()).contains("Publer: ya no da tiempo");
        assertThat(calls).containsExactly("POST /api/v1/create-post", "POST /api/v1/get-upload-url", "PUT /upload/m-1",
                "POST /api/v1/complete-media/m-1", "PUT /api/v1/update-post/g-1");
        JsonNode created = json.readTree(bodies.get("POST /api/v1/create-post"));
        assertThat(created.get("platforms").get(0).asString()).isEqualTo("instagram-111");
        assertThat(created.get("platformSettings").get("instagram").get("videoType").asString()).isEqualTo("STORIES");
        assertThat(created.has("scheduledTime")).isFalse();
        assertThat(json.readTree(bodies.get("PUT /api/v1/update-post/g-1")).get("scheduledTime").asString()).isEqualTo(at.toString());
        assertThat(bodies.get("PUT /upload/m-1").length()).isPositive();

        // Pasada la hora la consulta: aún publicando, y a los 5 minutos ya está con su enlace
        CLOCK.set(at.plus(Duration.ofMinutes(16)));
        engine.tick();
        assertThat(job(id).get("status").asString()).isEqualTo("CHECKING");
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHING");
        postStatus = "published";
        CLOCK.set(at.plus(Duration.ofMinutes(18)));
        engine.tick();
        assertThat(job(id).get("status").asString()).isEqualTo("CHECKING");
        CLOCK.set(at.plus(Duration.ofMinutes(22)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(job(id).get("url").asString()).isEqualTo("https://www.instagram.com/stories/elde_lito/1789");
    }

    @Test
    @Order(4)
    void leavingScheduledRemovesItFromPublora() throws Exception {
        Instant at = LocalDateTime.of(DAY, java.time.LocalTime.of(10, 0)).atZone(MADRID).toInstant();
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = approvedStory("10:00");
        schedule(id);
        assertThat(job(id).get("status").asString()).isEqualTo("SCHEDULED");
        calls.clear();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(calls).containsExactly("DELETE /api/v1/delete-post/g-1");
        assertThat(job(id).get("log").toString()).contains("Quitada de Publora");
        JsonNode p = publication(id);
        assertThat(!p.has("flag") || p.get("flag").isNull()).isTrue();
    }

    @Test
    @Order(5)
    void theCalendarShowsWhatIsOutsideSocialHub() throws Exception {
        CLOCK.reset();
        external.syncPublora("el-delito");
        JsonNode calendar = get("/api/promotions/poemario/external-calendar?from=" + DAY + "&to=" + DAY.plusDays(2));
        JsonNode items = calendar.get("items");
        assertThat(items.size()).isEqualTo(2);
        // La de SocialHub (g-1) lleva su publicación; la hecha a mano no; la de otra cuenta y el borrador no salen
        assertThat(items.get(0).get("external_id").asString()).isEqualTo("g-1");
        assertThat(items.get(0).get("publication_id").asString()).isEqualTo(DAY + "-instagram");
        assertThat(items.get(1).get("external_id").asString()).isEqualTo("g-9");
        assertThat(items.get(1).has("publication_id") && !items.get(1).get("publication_id").isNull()).isFalse();
        assertThat(items.get(1).get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(calendar.get("sources").toString()).contains("PUBLORA").contains("synced_at");

        // Encargo diario con Chrome: Publer y la red
        // El encargo diario sale desde las 7:00. El reloj nunca va por detrás del real: el lector de resultados
        // espera a que el archivo lleve un rato sin cambiar, y con el reloj atrasado no lo leería nunca.
        if (java.time.LocalTime.now(MADRID).isBefore(java.time.LocalTime.of(7, 5))) {
            CLOCK.set(LocalDate.now(MADRID).atTime(8, 0).atZone(MADRID).toInstant());
        } else {
            CLOCK.reset();
        }
        external.requestDaily("el-delito");
        external.requestDaily("el-delito"); // una vez al día
        Path pc = orders.resolve("para-claude-pc");
        waitFor(() -> Files.isDirectory(pc) && Files.list(pc).anyMatch(f -> f.getFileName().toString().endsWith(".json")), "encargo enviado");
        List<Path> sent;
        try (var s = Files.list(pc)) { sent = s.filter(f -> f.getFileName().toString().endsWith(".json")).toList(); }
        assertThat(sent).hasSize(1);
        JsonNode request = json.readTree(Files.readString(sent.getFirst()));
        assertThat(request.get("type").asString()).isEqualTo("socialhub.calendar-read");
        assertThat(request.get("inputs").get("brand").get("handle").asString()).isEqualTo("@elde_lito");
        String task = request.get("taskId").asString();

        Instant publerAt = LocalDate.now(MADRID).plusDays(3).atTime(19, 0).atZone(MADRID).toInstant();
        Instant networkAt = LocalDate.now(MADRID).minusDays(2).atTime(12, 0).atZone(MADRID).toInstant();
        Path result = Files.createDirectories(orders.resolve("de-claude")).resolve(task + "-1.json");
        Files.writeString(result, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed",
                 "outputs":{"items":[
                   {"source":"publer","platform":"INSTAGRAM","at":"%s","status":"scheduled","text":"Reel del jueves"},
                   {"source":"network","platform":"TIKTOK","at":"%s","status":"published","url":"https://www.tiktok.com/@litoques/video/1"}],
                  "summary":"2 encontradas"},
                 "agent":{"runtime":"tarea programada en el PC","model":"sonnet"}}""".formatted(task, publerAt, networkAt));
        Files.setLastModifiedTime(result, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(30)));
        LocalDate today = LocalDate.now(MADRID);
        waitFor(() -> get("/api/promotions/poemario/external-calendar?from=" + today.minusDays(3) + "&to=" + today.plusDays(4))
                .get("items").size() == 2, "calendario leído");
        JsonNode read = get("/api/promotions/poemario/external-calendar?from=" + today.minusDays(3) + "&to=" + today.plusDays(4));
        assertThat(read.get("items").get(0).get("source").asString()).isEqualTo("NETWORK");
        assertThat(read.get("items").get(1).get("source_label").asString()).isEqualTo("Publer");
        assertThat(read.get("sources").toString()).contains("PUBLER").contains("NETWORK");
        CLOCK.reset();
    }

    @Test
    @Order(6)
    void publoraErrorsGoToTheNextRoute() throws Exception {
        Instant at = LocalDateTime.of(DAY, java.time.LocalTime.of(11, 0)).atZone(MADRID).toInstant();
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = approvedStory("11:00");
        limitReached = true;
        schedule(id);
        JsonNode job = job(id);
        // Sin tiempo para otra ronda: a mano
        assertThat(job.get("status").asString()).isEqualTo("MANUAL");
        assertThat(job.get("log").toString()).contains("POST_LIMIT_REACHED").contains("API oficial: no configurada");
        limitReached = false;

        // Sin clave, Publora figura como no configurada
        write(delete("/api/publora/key"), "").andExpect(status().isNoContent());
        assertThat(get("/api/publora").get("configured").asBoolean()).isFalse();
        assertThat(Files.exists(dataDir.resolve("secretos/publora.key"))).isFalse();
        CLOCK.reset();
    }

    // ---------------------------------------------------------------- utilidades

    private String approvedStory(String time) throws Exception {
        String body = write(post("/api/promotions/poemario/publications"), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Historia %s","scheduled_on":"%s",
                 "scheduled_time":"%s","initial_status":"IN_PRODUCTION"}""".formatted(time, DAY, time))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asString();
        write(post("/api/publications/" + id + "/files"), "{\"path\":" + q(story) + "}").andExpect(status().isCreated());
        for (String s : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"" + s + "\"}").andExpect(status().isOk());
        }
        return id;
    }

    private void schedule(String id) throws Exception {
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"SCHEDULED\"}").andExpect(status().isOk());
    }

    private JsonNode job(String id) throws Exception { return get("/api/publications/" + id + "/publish-job"); }

    private JsonNode publication(String id) throws Exception {
        for (JsonNode p : get("/api/promotions/poemario/publications").get("items")) {
            if (p.get("id").asString().equals(id)) return p;
        }
        throw new AssertionError("No existe " + id);
    }

    private JsonNode get(String url) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andReturn().getResponse().getContentAsString());
    }

    private String q(Object text) throws Exception { return json.writeValueAsString(text.toString()); }

    private void waitFor(java.util.concurrent.Callable<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.call()) {
            assertThat(System.currentTimeMillis()).as(what).isLessThan(deadline);
            Thread.sleep(150);
        }
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
