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
    static Path story;
    static HttpServer publora;
    /** Lo que ha recibido el Publora de mentira: «MÉTODO ruta» y cuerpo. */
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final Map<String, String> bodies = new ConcurrentHashMap<>();
    static volatile String postStatus = "scheduled";
    static volatile boolean limitReached = false;

    @Autowired MockMvc mvc;
    @Autowired PublishingEngine engine;
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
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"APPROVED\",\"location\":" + q(approved) + "}]}")
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

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
