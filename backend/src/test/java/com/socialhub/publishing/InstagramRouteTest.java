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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * API oficial de Instagram contra un Meta de mentira (servidor HTTP local; nunca se llama a Meta de verdad): clave y
 * cuenta de la marca, reel por subida resumible, carrusel, imágenes con enlaces públicos (encargo a Claude, enlaces que
 * llegan, que no llegan o que no son imagen), contenedor con error, límite diario, clave inválida y tiempo agotado al
 * publicar (revisión sin duplicar).
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
        "socialhub.publishing.initial-delay-millis=3600000",
        "socialhub.instagram.poll-millis=10",
        "socialhub.instagram.max-polls=5"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstagramRouteTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final String GOOD_KEY = "EAAB.clave-de-prueba-0123456789";
    static final String IG_ID = "17841400000000000";
    static final PublishingEngineTest.MovableClock CLOCK = new PublishingEngineTest.MovableClock();
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(10);
    static final LocalDate DAY2 = DAY.plusDays(1);

    @TempDir static Path drive;
    static Path dataDir;
    static Path approved;
    static Path orders;
    static Path publicFolder;
    static Path reel;
    static Path reel2;
    static Path photo;
    static HttpServer meta;

    /** Lo que ha recibido el Meta de mentira: «MÉTODO ruta». */
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final Map<String, String> bodies = new ConcurrentHashMap<>();
    static final Map<String, Map<String, String>> headers = new ConcurrentHashMap<>();
    static final List<String> mediaBodies = new CopyOnWriteArrayList<>();
    static final AtomicInteger containers = new AtomicInteger();
    static final AtomicInteger published = new AtomicInteger();
    static volatile boolean acceptKey = true;
    static volatile int quotaUsed = 0;
    static volatile boolean containerError = false;
    /** Qué hace {@code media_publish}: 0 publica, 1 se corta (500), 2 lo rechaza (400). */
    static volatile int publishMode = 0;
    /** Cómo consta el contenedor tras un {@code media_publish} que falló. */
    static volatile String containerAfterFailure = "FINISHED";
    static volatile boolean publishFailed = false;
    static volatile boolean permalinkMissing = false;
    static volatile boolean imageGoesBad = false;
    static final List<String> queries = new CopyOnWriteArrayList<>();

    @Autowired MockMvc mvc;
    @Autowired PublishingEngine engine;
    @Autowired PublishingQueries alerts;
    @Autowired com.socialhub.platform.postgres.ModeDataSources databases;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() { return CLOCK; }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "it-instagram");
        registry.add("socialhub.data-dir", dataDir::toString);
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", InstagramRouteTest::handle);
        meta.start();
        String base = "http://127.0.0.1:" + meta.getAddress().getPort();
        registry.add("socialhub.instagram.graph-url", () -> base);
        registry.add("socialhub.instagram.upload-url", () -> base);
        // Los enlaces de descarga los construye SocialHub con el id de Drive; en las pruebas apuntan a este servidor
        registry.add("socialhub.publishing.public-link-base", () -> base + "/img/dl?id=");
    }

    @AfterAll
    static void stop() { meta.stop(0); }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String call = ex.getRequestMethod() + " " + path;
        String type = "application/json";
        String reply;
        int code = 200;
        if (path.startsWith("/img/")) {
            // Las imágenes públicas (lo que Claude encuentra en Drive)
            String query = String.valueOf(ex.getRequestURI().getQuery());
            boolean html = query.contains("page-bad-0001") || (query.contains("switch-id-0001") && imageGoesBad);
            type = html ? "text/html" : "image/jpeg";
            reply = html ? "<html>Inicia sesión</html>" : "jpg";
        } else {
            calls.add(call);
            queries.add(call + "?" + ex.getRequestURI().getRawQuery());
            bodies.put(call, body);
            headers.put(call, Map.of("authorization", String.valueOf(ex.getRequestHeaders().getFirst("Authorization")),
                    "offset", String.valueOf(ex.getRequestHeaders().getFirst("offset")),
                    "file_size", String.valueOf(ex.getRequestHeaders().getFirst("file_size"))));
            if (!("OAuth " + GOOD_KEY).equals(ex.getRequestHeaders().getFirst("Authorization")) || !acceptKey) {
                code = 401;
                reply = "{\"error\":{\"message\":\"Invalid OAuth access token\",\"type\":\"OAuthException\",\"code\":190}}";
            } else if (path.endsWith("/me")) {
                reply = "{\"id\":\"999\",\"name\":\"Usuario del sistema\"}";
            } else if (path.equals("/v23.0/" + IG_ID)) {
                reply = "{\"id\":\"" + IG_ID + "\",\"username\":\"elde_lito\"}";
            } else if (path.endsWith("/content_publishing_limit")) {
                reply = "{\"data\":[{\"quota_usage\":" + quotaUsed + ",\"config\":{\"quota_total\":100,\"quota_duration\":86400}}]}";
            } else if (path.equals("/v23.0/" + IG_ID + "/media")) {
                mediaBodies.add(URLDecoder.decode(body, StandardCharsets.UTF_8));
                reply = "{\"id\":\"c-" + containers.incrementAndGet() + "\"}";
            } else if (path.startsWith("/ig-api-upload/")) {
                reply = "{\"success\":true,\"message\":\"Upload successful.\"}";
            } else if (path.startsWith("/v23.0/c-")) {
                String state = containerError ? "ERROR" : publishFailed ? containerAfterFailure : "FINISHED";
                reply = "{\"status_code\":\"" + state + "\"}";
            } else if (path.equals("/v23.0/" + IG_ID + "/media_publish")) {
                if (publishMode == 1) {
                    publishFailed = true;
                    code = 500;
                    reply = "{\"error\":{\"message\":\"Service temporarily unavailable\",\"code\":2}}";
                } else if (publishMode == 2) {
                    publishFailed = true;
                    code = 400;
                    reply = "{\"error\":{\"message\":\"Invalid parameter\",\"code\":100,\"error_subcode\":2207010}}";
                } else {
                    reply = "{\"id\":\"m-" + published.incrementAndGet() + "\"}";
                }
            } else if (path.startsWith("/v23.0/m-")) {
                reply = permalinkMissing ? "{\"id\":\"x\"}"
                        : "{\"permalink\":\"https://www.instagram.com/reel/" + path.substring(path.lastIndexOf('/') + 1) + "/\"}";
            } else {
                code = 400;
                reply = "{\"error\":{\"message\":\"Unsupported get request. Object does not exist\",\"code\":100}}";
            }
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) ex.getResponseBody().write(bytes);
        ex.close();
    }

    @BeforeAll
    static void folders() throws Exception {
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        publicFolder = orders.resolve("publico");
        reel = approved.resolve("reel.mp4");
        reel2 = approved.resolve("reel2.mp4");
        Files.write(reel, new byte[2048]);
        Files.write(reel2, new byte[1024]);
        BufferedImage image = new BufferedImage(1080, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1080, 1080);
        g.dispose();
        photo = approved.resolve("foto.jpg");
        ImageIO.write(image, "jpg", photo.toFile());
    }

    @BeforeEach
    void reset() {
        calls.clear();
        bodies.clear();
        mediaBodies.clear();
        acceptKey = true;
        quotaUsed = 0;
        containerError = false;
        publishMode = 0;
        publishFailed = false;
        containerAfterFailure = "FINISHED";
        permalinkMissing = false;
        imageGoesBad = false;
        queries.clear();
    }

    // ---------------------------------------------------------------- Clave y cuenta

    @Test
    @Order(1)
    void keyAndBrandAccountAreCheckedWithMeta() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(LocalDate.now(MADRID), DAY.plusDays(30))).andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"APPROVED\",\"location\":" + q(approved)
                + "},{\"role\":\"ORDERS\",\"location\":" + q(orders) + "}]}").andExpect(status().isOk());

        assertThat(get("/api/instagram").get("configured").asBoolean()).isFalse();
        // Sin clave no se puede comprobar ni guardar la cuenta
        write(post("/api/instagram/check"), "{}").andExpect(status().isBadRequest());
        write(put("/api/brands/el-delito/instagram-account"), "{\"ig_user_id\":\"" + IG_ID + "\"}").andExpect(status().isBadRequest());

        // Clave falsa: Meta la rechaza y no se guarda
        String bad = write(put("/api/instagram/key"), "{\"key\":\"EAAB.clave-falsa-0123456789\"}")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(bad).contains("Meta no acepta la clave").doesNotContain("clave-falsa-0123456789").doesNotContain("Invalid OAuth");
        // Se pide a Meta que conteste en español y su texto original no llega a Raúl
        assertThat(queries).anyMatch(c -> c.contains("locale=es_ES"));
        assertThat(get("/api/instagram").get("configured").asBoolean()).isFalse();

        JsonNode saved = json.readTree(write(put("/api/instagram/key"), "{\"key\":\"" + GOOD_KEY + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.get("key_hint").asString()).isEqualTo("…6789");
        assertThat(saved.toString()).doesNotContain(GOOD_KEY);
        assertThat(Files.readString(dataDir.resolve("secretos/instagram-api.key"))).isEqualTo(GOOD_KEY);
        assertThat(calls).contains("GET /v23.0/me");
        assertThat(headers.get("GET /v23.0/me").get("authorization")).isEqualTo("OAuth " + GOOD_KEY);

        // La cuenta de la marca: identificador válido → @usuario; inexistente → error de Meta
        write(put("/api/brands/el-delito/instagram-account"), "{\"ig_user_id\":\"abc\"}").andExpect(status().isBadRequest());
        String missing = write(put("/api/brands/el-delito/instagram-account"), "{\"ig_user_id\":\"12345678\"}")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(missing).contains("Meta no acepta lo enviado").doesNotContain("Unsupported get request");
        JsonNode account = json.readTree(write(put("/api/brands/el-delito/instagram-account"), "{\"ig_user_id\":\"" + IG_ID + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(account.get("username").asString()).isEqualTo("elde_lito");
        assertThat(get("/api/brands/el-delito/instagram-account").get("ig_user_id").asString()).isEqualTo(IG_ID);

        // Comprobar: GET /me y, por marca, la cuenta y el límite
        calls.clear();
        quotaUsed = 3;
        JsonNode check = json.readTree(write(post("/api/instagram/check"), "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(check.get("ok").asBoolean()).isTrue();
        assertThat(check.get("name").asString()).isEqualTo("Usuario del sistema");
        assertThat(check.get("brands").get(0).get("username").asString()).isEqualTo("elde_lito");
        assertThat(check.get("brands").get(0).get("publishing_used").asInt()).isEqualTo(3);
        assertThat(check.get("brands").get(0).get("publishing_total").asInt()).isEqualTo(100);
        assertThat(calls).contains("GET /v23.0/me", "GET /v23.0/" + IG_ID, "GET /v23.0/" + IG_ID + "/content_publishing_limit");
        // Su estado aparece en «Agentes y herramientas»
        boolean listed = false;
        for (JsonNode item : get("/api/armory-items").get("items")) {
            if ("instagram-api".equals(item.get("id").asString())) {
                listed = true;
                assertThat(item.get("status").asString()).isEqualTo("OK");
                assertThat(item.get("note").asString()).contains("@elde_lito");
            }
        }
        assertThat(listed).isTrue();
    }

    // ---------------------------------------------------------------- Vídeo: subida resumible

    @Test
    @Order(2)
    void aReelIsUploadedResumablyAndPublishedAtItsTime() throws Exception {
        Instant at = at(9);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Reel", 9, "Reel de las 9", List.of(reel));
        schedule(id);

        // Al programarla solo se comprueba que se puede: Meta no programa
        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(job.get("route").asString()).isEqualTo("API oficial");
        assertThat(calls).containsExactly("GET /v23.0/" + IG_ID);
        assertThat(publication(id).get("status").asString()).isEqualTo("SCHEDULED");

        // A la hora la publica SocialHub
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(job.get("external_id").asString()).isEqualTo("m-" + published.get());
        assertThat(job.get("url").asString()).startsWith("https://www.instagram.com/reel/m-");
        assertThat(mediaBodies).hasSize(1);
        assertThat(mediaBodies.getFirst()).contains("media_type=REELS").contains("upload_type=resumable")
                .contains("caption=Texto de Reel de las 9\n\n#poemas #lito");
        String upload = calls.stream().filter(c -> c.startsWith("POST /ig-api-upload/v23.0/c-")).findFirst().orElseThrow();
        assertThat(headers.get(upload).get("authorization")).isEqualTo("OAuth " + GOOD_KEY);
        assertThat(headers.get(upload).get("offset")).isEqualTo("0");
        assertThat(headers.get(upload).get("file_size")).isEqualTo("2048");
        assertThat(bodies.get(upload)).hasSize(2048);
        // Orden: límite, contenedor, subida, estado, publicar, enlace
        assertThat(calls.stream().map(c -> c.replaceAll("c-\\d+|m-\\d+", "X")).toList()).containsExactly(
                "GET /v23.0/" + IG_ID,
                "GET /v23.0/" + IG_ID + "/content_publishing_limit",
                "POST /v23.0/" + IG_ID + "/media",
                "POST /ig-api-upload/v23.0/X",
                "GET /v23.0/X",
                "POST /v23.0/" + IG_ID + "/media_publish",
                "GET /v23.0/X");
    }

    @Test
    @Order(3)
    void aCarouselOfVideosCreatesItsChildrenAndPublishesOnce() throws Exception {
        Instant at = at(10);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Carrusel", 10, "Carrusel de las 10", List.of(reel, reel2));
        schedule(id);
        assertThat(job(id).get("route").asString()).isEqualTo("API oficial");
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(mediaBodies).hasSize(3);
        assertThat(mediaBodies.get(0)).contains("is_carousel_item=true").contains("media_type=VIDEO");
        assertThat(mediaBodies.get(1)).contains("is_carousel_item=true");
        assertThat(mediaBodies.get(2)).contains("media_type=CAROUSEL").contains("children=c-").contains(",c-");
        assertThat(calls.stream().filter(c -> c.endsWith("/media_publish")).count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- Respaldo

    @Test
    @Order(4)
    void aContainerInErrorFallsBackWithTheReasonAndNeverPublishes() throws Exception {
        Instant at = at(11);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Reel", 11, "Reel con error", List.of(reel));
        schedule(id);
        containerError = true;
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(calls.stream().anyMatch(c -> c.endsWith("/media_publish"))).isFalse();
        JsonNode job = job(id);
        // A la hora ya no hay vía que llegue: aviso claro con el motivo y LEEME con el texto de «la hora ya pasó»
        assertThat(job.get("status").asString()).isEqualTo("FAILED");
        assertThat(job.get("log").toString()).doesNotContain("Publer");
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISH_ERROR");
        String flag = publication(id).get("flag").asString();
        assertThat(flag).startsWith("API oficial: Meta no pudo procesar el contenido (ERROR")
                .contains("La hora ya pasó: publícala tú cuanto antes o cámbiale la fecha");
        assertThat(leeme(id)).contains("La hora de publicación ya pasó").contains("cámbiale la fecha").doesNotContain("Publícala tú a su hora");
        assertThat(alert(id)).contains("La hora ya pasó");
    }

    @Test
    @Order(5)
    void aSpentDailyLimitFallsBack() throws Exception {
        Instant at = at(12);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Reel", 12, "Reel sin cupo", List.of(reel));
        schedule(id);
        quotaUsed = 100;
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(mediaBodies).isEmpty();
        assertThat(job(id).get("status").asString()).isEqualTo("FAILED");
        assertThat(job(id).get("last_error").asString()).contains("límite de publicaciones por la API agotado (100 de 100")
                .contains("La hora ya pasó");
        assertThat(publication(id).get("flag").asString()).contains("API oficial: límite");
        assertThat(alert(id)).contains("límite de publicaciones");
    }

    @Test
    @Order(6)
    void anInvalidKeyFallsBackAtScheduleTimeAndAtPublishTime() throws Exception {
        // Al programarla: Meta no acepta la clave → se sigue por otra vía desde el primer momento
        Instant at = at(13);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        acceptKey = false;
        String id = publication("Reel", 13, "Reel con clave mala", List.of(reel));
        schedule(id);
        JsonNode job = job(id);
        assertThat(job.get("log").toString()).contains("API oficial: Meta no acepta la clave");
        assertThat(job.get("route").asString()).isEqualTo("Publer");
        assertThat(publication(id).get("flag").asString()).startsWith("API oficial: Meta no acepta la clave");
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());

        // Al publicar: la clave deja de valer entre medias
        acceptKey = true;
        Instant at2 = at(14);
        CLOCK.set(at2.minus(Duration.ofHours(7)));
        String id2 = publication("Reel", 14, "Reel que pierde la clave", List.of(reel));
        schedule(id2);
        acceptKey = false;
        CLOCK.set(at2.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(job(id2).get("log").toString()).contains("Meta no acepta la clave");
        assertThat(job(id2).get("status").asString()).isEqualTo("FAILED");
        assertThat(publication(id2).get("flag").asString()).contains("La hora ya pasó");
        acceptKey = true;
    }

    @Test
    @Order(7)
    void aTimeoutOnPublishWithAnUnpublishedContainerFailsButNeverTriesTwice() throws Exception {
        // Meta se corta, pero el contenedor sigue en FINISHED: seguro que no se publicó
        Instant at = at(15);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Reel", 15, "Reel con corte", List.of(reel));
        schedule(id);
        publishMode = 1;
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISH_ERROR");
        assertThat(job(id).get("status").asString()).isEqualTo("FAILED");
        assertThat(calls.stream().filter(c -> c.endsWith("/media_publish")).count()).isEqualTo(1);
        // Tras el error se consultó el contenedor
        assertThat(calls.get(calls.size() - 1)).startsWith("GET /v23.0/c-");
        publishMode = 0;
        CLOCK.set(at.plus(Duration.ofHours(2)));
        engine.tick();
        engine.tick();
        assertThat(calls.stream().filter(c -> c.endsWith("/media_publish")).count()).isEqualTo(1);
        assertThat(job(id).get("log").toString()).doesNotContain("Publer");
    }

    @Test
    @Order(8)
    void aPublishErrorWithAPublishedOrUnknownContainerIsADoubtAndAlsoOnA4xx() throws Exception {
        // Meta se corta y el contenedor consta como PUBLISHED: duda, nunca reintento ni Publer
        Instant at = at(16);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("Reel", 16, "Reel que sí se publicó", List.of(reel));
        schedule(id);
        publishMode = 1;
        containerAfterFailure = "PUBLISHED";
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        JsonNode p = publication(id);
        assertThat(p.get("status").asString()).isEqualTo("PUBLISH_ERROR");
        assertThat(p.get("flag").asString()).startsWith("Revisar antes de reintentar").contains("consta que la publicó")
                .contains("Mira en Instagram");
        assertThat(alertTitle(id)).startsWith("Revisar antes de reintentar");
        assertThat(alert(id)).contains("Mira en Instagram");

        // Un 4xx tampoco se da por «no se publicó» sin mirar el contenedor: si no está en FINISHED, duda
        Instant at2 = at(17);
        CLOCK.set(at2.minus(Duration.ofHours(7)));
        String id2 = publication("Reel", 17, "Reel con rechazo", List.of(reel));
        schedule(id2);
        publishFailed = false;
        publishMode = 2;
        containerAfterFailure = "IN_PROGRESS";
        CLOCK.set(at2.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id2).get("flag").asString()).startsWith("Revisar antes de reintentar").contains("el contenedor quedó en IN_PROGRESS");

        // Un 4xx con el contenedor en FINISHED sí es un fallo seguro, con el mensaje en español
        Instant at3 = at(18);
        CLOCK.set(at3.minus(Duration.ofHours(7)));
        String id3 = publication("Reel", 18, "Reel rechazado", List.of(reel));
        schedule(id3);
        publishFailed = false;
        containerAfterFailure = "FINISHED";
        CLOCK.set(at3.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(job(id3).get("status").asString()).isEqualTo("FAILED");
        assertThat(publication(id3).get("flag").asString()).contains("el texto es demasiado largo para Instagram")
                .doesNotContain("Invalid parameter").contains("La hora ya pasó");
    }

    // ---------------------------------------------------------------- Imágenes: enlaces públicos

    @Test
    @Order(9)
    void anImageWithoutAPublicFolderGoesToPubler() throws Exception {
        Instant at = at(16);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("Post", 16, "Foto sin carpeta pública", List.of(photo));
        schedule(id);
        JsonNode job = job(id);
        assertThat(job.get("log").toString()).contains("API oficial: las imágenes necesitan la carpeta pública");
        assertThat(job.get("route").asString()).isEqualTo("Publer");
        assertThat(publication(id).get("flag").asString()).startsWith("API oficial:");
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(10)
    void linksThatArriveLetTheOfficialApiPublishAndThePublicFolderIsCleaned() throws Exception {
        Files.createDirectories(publicFolder);
        Instant at = at(17);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        String id = publication("Post", 17, "Foto con enlaces", List.of(photo));
        schedule(id);

        // Se deja el JPEG (convertido del PNG) en «publico» y se encarga a Claude su enlace, con el límite a 9 h
        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("WORKING");
        assertThat(job.get("route").asString()).isEqualTo("API oficial");
        List<Path> staged = list(publicFolder);
        assertThat(staged).hasSize(1);
        assertThat(staged.getFirst().getFileName().toString()).matches("[0-9a-f]{20}\\.jpg");
        assertThat(ImageIO.read(staged.getFirst().toFile())).isNotNull();
        JsonNode request = linkRequest(staged.getFirst().getFileName().toString());
        assertThat(request.get("type").asString()).isEqualTo("socialhub.public-link");
        assertThat(request.get("inputs").get("files").get(0).asString()).isEqualTo(staged.getFirst().getFileName().toString());
        assertThat(Instant.parse(request.get("deadline").asString())).isEqualTo(PublerRoute.handoverBy(at));
        // «urgent» sale calculado con el límite del encargo y el momento en que Claude puede verlo
        assertThat(request.get("urgent").asBoolean()).isEqualTo(com.socialhub.ai.ClaudePasses.urgent(false,
                PublerRoute.handoverBy(at), com.socialhub.ai.ClaudePickups.visibleAt(CLOCK.instant())));

        answer(request, "[{\"file\":\"" + staged.getFirst().getFileName() + "\",\"id\":\"drive-ok-0001\"}]");
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "enlaces recibidos");
        assertThat(job(id).get("route").asString()).isEqualTo("API oficial");

        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(mediaBodies).hasSize(1);
        assertThat(mediaBodies.getFirst()).contains("image_url=" + url("/img/dl?id=drive-ok-0001")).doesNotContain("upload_type");
        assertThat(list(publicFolder)).isEmpty();
    }

    @Test
    @Order(11)
    void linksThatDoNotArriveInTimeGoToPublerBeforeItsLimit() throws Exception {
        Instant at = at(18);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        String id = publication("Post", 18, "Foto sin enlaces", List.of(photo));
        schedule(id);
        assertThat(job(id).get("status").asString()).isEqualTo("WORKING");
        assertThat(list(publicFolder)).hasSize(1);

        // Pasa el corte (9 h antes): Publer aún llega (necesita 8 h)
        CLOCK.set(PublerRoute.handoverBy(at).plusSeconds(60));
        engine.tick();
        JsonNode job = job(id);
        assertThat(job.get("route").asString()).isEqualTo("Publer");
        assertThat(job.get("log").toString()).contains("API oficial: Claude no lo hizo a tiempo");
        assertThat(publication(id).get("flag").asString()).startsWith("API oficial: los enlaces públicos");
        assertThat(list(publicFolder)).isEmpty();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(12)
    void aLinkThatIsNotAnImageGoesToPubler() throws Exception {
        Instant at = at(19);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        String id = publication("Post", 19, "Foto con enlace malo", List.of(photo));
        schedule(id);
        Path staged = list(publicFolder).getFirst();
        answer(linkRequest(staged.getFileName().toString()),
                "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"page-bad-0001\"}]");
        waitFor(() -> "Publer".equals(job(id).get("route").asString()), "enlace descartado");
        assertThat(job(id).get("log").toString()).contains("no devuelve una imagen");
        assertThat(list(publicFolder)).isEmpty();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(13)
    void aLinkThatStopsBeingAnImageAtPublishTimeFailsWithoutPublishing() throws Exception {
        Instant at = at(20);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        String id = publication("Post", 20, "Foto que se rompe", List.of(photo));
        schedule(id);
        Path staged = list(publicFolder).getFirst();
        answer(linkRequest(staged.getFileName().toString()),
                "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"switch-id-0001\"}]");
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "enlaces recibidos");
        imageGoesBad = true;
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(mediaBodies).isEmpty();
        assertThat(job(id).get("log").toString()).contains("no devuelve una imagen");
        assertThat(publication(id).get("flag").asString()).contains("API oficial:").contains("La hora ya pasó");
        assertThat(job(id).get("status").asString()).isEqualTo("FAILED");
        assertThat(list(publicFolder)).isEmpty();
    }

    @Test
    @Order(14)
    void leavingScheduledCleansThePublicFolder() throws Exception {
        Instant at = at(21);
        CLOCK.set(at.minus(Duration.ofHours(24)));
        String id = publication("Post", 21, "Foto que se descarta", List.of(photo));
        schedule(id);
        assertThat(list(publicFolder)).hasSize(1);
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(list(publicFolder)).isEmpty();
    }

    // ---------------------------------------------------------------- SocialHub cerrado a la hora

    @Test
    @Order(15)
    void ifSocialHubWasClosedItPublishesWhenItOpensWithNoTimeLimitAndWarnsOfTheDelay() throws Exception {
        Instant at = at(DAY2, 11);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("INSTAGRAM", DAY2, "Reel", 11, "Reel con retraso", List.of(reel));
        schedule(id);
        // Se abre 3 h 20 min después de su hora
        CLOCK.set(at.plus(Duration.ofMinutes(200)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(publication(id).get("flag").asString()).startsWith("Publicada con 3 h 20 min de retraso");
        assertThat(alertTitle(id)).startsWith("Publicada con retraso");
        assertThat(alert(id)).contains("3 h 20 min de retraso");
        assertThat(job(id).get("log").toString()).doesNotContain("Publer").doesNotContain("LEEME");

        // Sin límite de tiempo: al día siguiente también
        Instant at2 = at(DAY2, 12);
        CLOCK.set(at2.minus(Duration.ofHours(7)));
        String id2 = publication("INSTAGRAM", DAY2, "Reel", 12, "Reel de un día después", List.of(reel));
        schedule(id2);
        CLOCK.set(at2.plus(Duration.ofHours(26)));
        engine.tick();
        assertThat(publication(id2).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(publication(id2).get("flag").asString()).startsWith("Publicada con 26 h de retraso");
    }

    @Test
    @Order(16)
    void twoReelsAtTheSameTimeBothPublishInTheSameRound() throws Exception {
        Instant at = at(DAY2, 13);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String a = publication("INSTAGRAM", DAY2, "Reel", 13, "Reel gemelo A", List.of(reel));
        String b = publication("INSTAGRAM", DAY2, "Reel", 13, "Reel gemelo B", List.of(reel2));
        schedule(a);
        schedule(b);
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        for (String id : List.of(a, b)) {
            assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
            assertThat(publication(id).has("flag") && !publication(id).get("flag").isNull()).isFalse();
        }
    }

    @Test
    @Order(17)
    void startingToPublishWithoutAResultIsADoubtAfterARestart() throws Exception {
        Instant at = at(DAY2, 14);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("INSTAGRAM", DAY2, "Reel", 14, "Reel con reinicio", List.of(reel));
        schedule(id);
        // Como si SocialHub se hubiera cerrado a mitad de la publicación: la marca está puesta y no hay resultado
        sql("update publish_job set publish_started_at = now() where publication_id = '" + id + "'");
        calls.clear();
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(calls.stream().anyMatch(c -> c.endsWith("/media_publish"))).isFalse();
        assertThat(job(id).get("status").asString()).isEqualTo("FAILED");
        assertThat(publication(id).get("flag").asString()).startsWith("Revisar antes de reintentar").contains("SocialHub se cerró");
        assertThat(alertTitle(id)).startsWith("Revisar antes de reintentar");
    }

    @Test
    @Order(18)
    void publishedWithoutALinkKeepsCheckingAndIsConfirmedOrADoubtAfter24HoursFromWhenItWasPublished() throws Exception {
        // Meta da el identificador pero aún no el enlace: «Comprobando», y «Publicada» cuando llega el enlace
        Instant at = at(DAY2, 15);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("INSTAGRAM", DAY2, "Reel", 15, "Reel sin enlace aún", List.of(reel));
        schedule(id);
        permalinkMissing = true;
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(job(id).get("status").asString()).isEqualTo("CHECKING");
        assertThat(job(id).get("external_id").asString()).startsWith("m-");
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHING");
        permalinkMissing = false;
        CLOCK.set(at.plus(Duration.ofMinutes(8)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(job(id).get("url").asString()).startsWith("https://www.instagram.com/reel/m-");

        // Publicada con 3 h de retraso y el enlace no llega: las 24 h cuentan desde que se publicó, no desde la hora prevista
        Instant at2 = at(DAY2, 16);
        CLOCK.set(at2.minus(Duration.ofHours(7)));
        String id2 = publication("INSTAGRAM", DAY2, "Reel", 16, "Reel que no da enlace", List.of(reel));
        schedule(id2);
        permalinkMissing = true;
        Instant published = at2.plus(Duration.ofHours(3));
        CLOCK.set(published);
        engine.tick();
        assertThat(job(id2).get("status").asString()).isEqualTo("CHECKING");
        CLOCK.set(published.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(50)));
        engine.tick();
        assertThat(job(id2).get("status").asString()).isEqualTo("CHECKING");
        CLOCK.set(published.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(10)));
        engine.tick();
        // Con identificador, nunca es un simple error: duda, y se mira en Instagram
        assertThat(job(id2).get("status").asString()).isEqualTo("FAILED");
        assertThat(publication(id2).get("flag").asString()).startsWith("Revisar antes de reintentar")
                .contains("Meta la publicó con el identificador m-").contains("Mira en Instagram");
    }

    // ---------------------------------------------------------------- Corte previo

    @Test
    @Order(19)
    void everythingIsCheckedAtTheCutoffAndAFailureThereGoesToPublerWithTheReason() throws Exception {
        // Todo bien: se anota la comprobación y a la hora se publica
        Instant at = at(DAY2, 17);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("INSTAGRAM", DAY2, "Reel", 17, "Reel con corte previo", List.of(reel));
        schedule(id);
        assertThat(job(id).get("log").toString()).doesNotContain("comprobado todo");
        calls.clear();
        CLOCK.set(PublerRoute.handoverBy(at).minusSeconds(60));
        engine.tick();
        assertThat(calls).isEmpty();
        CLOCK.set(PublerRoute.handoverBy(at).plusSeconds(60));
        engine.tick();
        assertThat(job(id).get("log").toString()).contains("comprobado todo antes del corte");
        assertThat(job(id).get("route").asString()).isEqualTo("API oficial");
        assertThat(calls).contains("GET /v23.0/" + IG_ID);
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");

        // La clave deja de valer antes del corte: pasa a Publer, que aún llega, con el motivo
        Instant at2 = at(DAY2, 18);
        CLOCK.set(at2.minus(Duration.ofHours(20)));
        String id2 = publication("INSTAGRAM", DAY2, "Reel", 18, "Reel cuya clave se cae", List.of(reel));
        schedule(id2);
        acceptKey = false;
        CLOCK.set(PublerRoute.handoverBy(at2).plusSeconds(60));
        engine.tick();
        assertThat(job(id2).get("route").asString()).isEqualTo("Publer");
        assertThat(publication(id2).get("flag").asString()).startsWith("API oficial: Meta no acepta la clave");
        assertThat(alertTitle(id2)).startsWith("La API oficial de Instagram no pudo");
        write(post("/api/publications/" + id2 + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(20)
    void aLinkThatStopsBeingAnImageIsCaughtAtTheCutoffAndGoesToPubler() throws Exception {
        Files.createDirectories(publicFolder);
        Instant at = at(DAY2, 19);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("INSTAGRAM", DAY2, "Post", 19, "Foto que se rompe antes del corte", List.of(photo));
        schedule(id);
        Path staged = list(publicFolder).getFirst();
        answer(linkRequest(staged.getFileName().toString()), "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"switch-id-0001\"}]");
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "enlaces recibidos");
        imageGoesBad = true;
        CLOCK.set(PublerRoute.handoverBy(at).plusSeconds(60));
        engine.tick();
        assertThat(job(id).get("route").asString()).isEqualTo("Publer");
        assertThat(publication(id).get("flag").asString()).contains("no devuelve una imagen");
        assertThat(list(publicFolder)).isEmpty();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(21)
    void linksThatComeBackNeedsHumanOrFailedGoToPublerAtOnce() throws Exception {
        Files.createDirectories(publicFolder);
        for (String status : List.of("needs_human", "failed")) {
            int hour = status.equals("failed") ? 21 : 20;
            Instant at = at(DAY2, hour);
            CLOCK.set(at.minus(Duration.ofHours(24)));
            String id = publication("INSTAGRAM", DAY2, "Post", hour, "Foto con encargo " + status, List.of(photo));
            schedule(id);
            Path staged = list(publicFolder).getFirst();
            answerWith(linkRequest(staged.getFileName().toString()), status, "No veo la carpeta publico");
            waitFor(() -> "Publer".equals(job(id).get("route").asString()), "sigue por Publer");
            assertThat(publication(id).get("flag").asString()).startsWith("API oficial:").contains("No veo la carpeta publico");
            assertThat(list(publicFolder)).isEmpty();
            write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        }
    }

    @Test
    @Order(22)
    void anOverdueLinksOrderGoesToPublerWithoutWaitingForTheCutoff() throws Exception {
        Files.createDirectories(publicFolder);
        Instant at = at(DAY2.plusDays(1), 22);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("INSTAGRAM", DAY2.plusDays(1), "Post", 22, "Foto con encargo tardío", List.of(photo));
        schedule(id);
        assertThat(job(id).get("status").asString()).isEqualTo("WORKING");
        // Su pasada (la completa de las 5:30; el corte es a las 10:20) ya pasó hace más de media hora y nadie contesta, pero aún falta para el corte
        CLOCK.set(at.minus(Duration.ofHours(16)));
        assertThat(CLOCK.instant()).isBefore(PublerRoute.handoverBy(at));
        engine.tick();
        assertThat(job(id).get("route").asString()).isEqualTo("Publer");
        assertThat(job(id).get("log").toString()).contains("va con retraso");
        assertThat(list(publicFolder)).isEmpty();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(23)
    void linksThatArriveAfterTheCutoffAreDiscarded() throws Exception {
        Files.createDirectories(publicFolder);
        Instant at = at(DAY2.plusDays(1), 20);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("INSTAGRAM", DAY2.plusDays(1), "Post", 20, "Foto con enlaces tardíos", List.of(photo));
        schedule(id);
        Path staged = list(publicFolder).getFirst();
        JsonNode request = linkRequest(staged.getFileName().toString());
        CLOCK.set(PublerRoute.handoverBy(at).plusSeconds(60));
        answer(request, "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"drive-ok-0002\"}]");
        waitFor(() -> list(publicFolder).isEmpty(), "imágenes descartadas");
        assertThat(job(id).get("status").asString()).isEqualTo("WORKING");
        engine.tick();
        assertThat(job(id).get("route").asString()).isEqualTo("Publer");
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(24)
    void aLinkWithAnInvalidDriveIdIsRejected() throws Exception {
        Files.createDirectories(publicFolder);
        Instant at = at(DAY2.plusDays(1), 21);
        CLOCK.set(at.minus(Duration.ofHours(20)));
        String id = publication("INSTAGRAM", DAY2.plusDays(1), "Post", 21, "Foto con id raro", List.of(photo));
        schedule(id);
        Path staged = list(publicFolder).getFirst();
        // Claude solo da el id: un enlace que traiga él no se usa y un id con rarezas se rechaza
        answer(linkRequest(staged.getFileName().toString()),
                "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"../../etc/passwd?x=1\",\"url\":\"https://evil.example/x.jpg\"}]");
        waitFor(() -> "Publer".equals(job(id).get("route").asString()), "id rechazado");
        assertThat(job(id).get("log").toString()).contains("no es válido");
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Historias y TikTok

    @Test
    @Order(25)
    void storiesGoOutAsVideoAndAsImage() throws Exception {
        Instant at = at(DAY2, 9);
        CLOCK.set(at.minus(Duration.ofHours(7)));
        String id = publication("INSTAGRAM", DAY2, "Historia", 9, "Historia de vídeo", List.of(reel));
        schedule(id);
        CLOCK.set(at.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(mediaBodies).hasSize(1);
        assertThat(mediaBodies.getFirst()).contains("media_type=STORIES").contains("upload_type=resumable").doesNotContain("caption");

        mediaBodies.clear();
        Files.createDirectories(publicFolder);
        Instant at2 = at(DAY2, 10);
        CLOCK.set(at2.minus(Duration.ofHours(14)));
        String id2 = publication("INSTAGRAM", DAY2, "Historia", 10, "Historia de imagen", List.of(photo));
        schedule(id2);
        Path staged = list(publicFolder).getFirst();
        answer(linkRequest(staged.getFileName().toString()), "[{\"file\":\"" + staged.getFileName() + "\",\"id\":\"drive-st-0001\"}]");
        waitFor(() -> "SCHEDULED".equals(job(id2).get("status").asString()), "enlaces recibidos");
        CLOCK.set(at2.plus(Duration.ofMinutes(1)));
        engine.tick();
        assertThat(publication(id2).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(mediaBodies).hasSize(1);
        assertThat(mediaBodies.getFirst()).contains("media_type=STORIES").contains("image_url=" + url("/img/dl?id=drive-st-0001"))
                .doesNotContain("caption").doesNotContain("upload_type");
        assertThat(list(publicFolder)).isEmpty();
    }

    @Test
    @Order(26)
    void tikTokSkipsTheOfficialRouteEvenWithTheKeyConfigured() throws Exception {
        Instant at = at(DAY2, 17);
        CLOCK.set(at.minus(Duration.ofHours(30)));
        String id = publication("TIKTOK", DAY2.plusDays(1), "Reel", 17, "TikTok con la API configurada", List.of(reel));
        schedule(id);
        assertThat(job(id).get("route").asString()).isEqualTo("Publer");
        assertThat(job(id).get("log").toString()).doesNotContain("API oficial");
        assertThat(calls).isEmpty();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(27)
    void deletingTheKeyLeavesTheRouteUnconfigured() throws Exception {
        write(delete("/api/instagram/key"), "").andExpect(status().isNoContent());
        assertThat(get("/api/instagram").get("configured").asBoolean()).isFalse();
        assertThat(Files.exists(dataDir.resolve("secretos/instagram-api.key"))).isFalse();
        CLOCK.reset();
    }

    // ---------------------------------------------------------------- utilidades

    private static Instant at(int hour) { return at(DAY, hour); }

    private static Instant at(LocalDate day, int hour) {
        return LocalDateTime.of(day, LocalTime.of(hour, 0)).atZone(MADRID).toInstant();
    }

    private static String url(String path) { return "http://127.0.0.1:" + meta.getAddress().getPort() + path; }

    private String publication(String type, int hour, String title, List<Path> files) throws Exception {
        return publication("INSTAGRAM", DAY, type, hour, title, files);
    }

    private String publication(String platform, LocalDate day, String type, int hour, String title, List<Path> files) throws Exception {
        String time = String.format("%02d:00", hour);
        String body = write(post("/api/promotions/poemario/publications"), """
                {"platform":"%s","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"%s",
                 "caption":"Texto de %s","hashtags":"#poemas #lito","initial_status":"IN_PRODUCTION"}"""
                .formatted(platform, type, title, day, time, title)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asString();
        for (Path f : files) {
            write(post("/api/publications/" + id + "/files"), "{\"path\":" + q(f) + "}").andExpect(status().isCreated());
        }
        for (String s : List.of("DRAFT", "IN_REVIEW")) {
            write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"" + s + "\"}").andExpect(status().isOk());
        }
        // Avisos de calidad que no vienen al caso (por ejemplo, no se pueden leer las dimensiones de un vídeo de mentira)
        for (JsonNode issue : get("/api/publications/" + id + "/quality").get("issues")) {
            if (issue.has("accepted") && issue.get("accepted").asBoolean()) continue;
            write(post("/api/publications/" + id + "/quality-acceptances"),
                    "{\"key\":" + q(issue.get("key").asString()) + ",\"reason\":\"es una prueba\"}");
        }
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        return id;
    }

    private void schedule(String id) throws Exception {
        calls.clear();
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"SCHEDULED\"}").andExpect(status().isOk());
    }

    /** El encargo «Enlaces públicos» enviado a para-claude para ese archivo. */
    private JsonNode linkRequest(String fileName) throws Exception {
        JsonNode[] found = new JsonNode[1];
        waitFor(() -> {
            for (Path f : list(orders.resolve("para-claude"))) {
                String text = Files.readString(f);
                if (text.contains("socialhub.public-link") && text.contains(fileName)) {
                    found[0] = json.readTree(text);
                    return true;
                }
            }
            return false;
        }, "encargo de enlaces enviado");
        return found[0];
    }

    private void answer(JsonNode request, String links) throws Exception {
        String task = request.get("taskId").asString();
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(task + "-" + System.nanoTime() + ".json");
        Files.writeString(file, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed",
                 "outputs":{"links":%s,"summary":"enlaces encontrados"},
                 "agent":{"runtime":"tarea programada en la nube","model":"haiku"}}""".formatted(task, links));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) { return s.filter(Files::isRegularFile).sorted().toList(); }
    }

    /** El LEEME que dejó SocialHub para publicarla a mano. */
    private static String leeme(String id) throws IOException {
        return Files.readString(approved.resolve("LEEME-publicar-a-mano-" + id + ".txt"));
    }

    /** El texto del aviso de «Necesita atención» de esa publicación. */
    private String alert(String id) {
        return alerts.publishAlerts().stream().filter(a -> a.publicationId().equals(id)).map(PublishingQueries.PublishAlert::detail)
                .findFirst().orElseThrow(() -> new AssertionError("Sin aviso en «Necesita atención» para " + id));
    }

    private String alertTitle(String id) {
        return alerts.publishAlerts().stream().filter(a -> a.publicationId().equals(id)).map(PublishingQueries.PublishAlert::title)
                .findFirst().orElseThrow(() -> new AssertionError("Sin aviso en «Necesita atención» para " + id));
    }

    private void sql(String sql) throws Exception {
        try (var c = java.sql.DriverManager.getConnection(databases.jdbcUrl(com.socialhub.platform.AppMode.REAL), databases.user(), "");
             var st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** El resultado de Claude con otro estado ({@code failed} o {@code needs_human}). */
    private void answerWith(JsonNode request, String status, String error) throws Exception {
        String task = request.get("taskId").asString();
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(task + "-" + System.nanoTime() + ".json");
        Files.writeString(file, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"%s","outputs":{},"error":"%s",
                 "agent":{"runtime":"tarea programada en la nube","model":"haiku"}}""".formatted(task, status, error));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
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
