package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
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
 * Flujo «Vida entera de una publicación» (manual, §5 y §6), con UNA sola publicación de principio a fin: propuesta de
 * Claude → Raúl la aprueba (en producción) → el spec genera su reel de verdad (Java2D y FFmpeg) → Claude escribe el
 * texto → revisión y «Aprobada» sin avisos → «Programada» por la API oficial de Instagram (Meta simulada por HTTP) →
 * a la hora SocialHub la publica y guarda el enlace → a los 7 días se leen las métricas y entran en los totales.
 */
@FlowTest
@TestPropertySource(properties = {
        "socialhub.publishing.initial-delay-millis=200",
        "socialhub.publishing.tick-millis=300",
        "socialhub.metrics.initial-delay-millis=200",
        "socialhub.metrics.tick-millis=300"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostLifecycleFlowTest {

    static final String KEY = "EAAB.clave-de-prueba-0123456789";
    static final String IG_ID = "17841400000000000";
    static final String ID = "2026-11-16-instagram";

    @TempDir static Path drive;
    static Path orders;
    static Path material;
    static Path generated;
    static Path page;
    static HttpServer meta;
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static final List<String> mediaBodies = new CopyOnWriteArrayList<>();
    static final AtomicInteger containers = new AtomicInteger();
    static final AtomicInteger published = new AtomicInteger();
    static Instant at;
    static Instant publishedAt;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", PostLifecycleFlowTest::handle);
        meta.start();
        String base = "http://127.0.0.1:" + meta.getAddress().getPort();
        registry.add("socialhub.instagram.graph-url", () -> base);
        registry.add("socialhub.instagram.upload-url", () -> base);
    }

    @AfterAll
    static void stop() { meta.stop(0); }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(ex.getRequestMethod() + " " + path);
        String reply;
        int code = 200;
        if (!("OAuth " + KEY).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            code = 401;
            reply = "{\"error\":{\"message\":\"Invalid OAuth access token\",\"type\":\"OAuthException\",\"code\":190}}";
        } else if (path.endsWith("/me")) {
            reply = "{\"id\":\"999\",\"name\":\"Usuario del sistema\"}";
        } else if (path.equals("/v23.0/" + IG_ID)) {
            reply = "{\"id\":\"" + IG_ID + "\",\"username\":\"elde_lito\"}";
        } else if (path.endsWith("/content_publishing_limit")) {
            reply = "{\"data\":[{\"quota_usage\":0,\"config\":{\"quota_total\":100,\"quota_duration\":86400}}]}";
        } else if (path.equals("/v23.0/" + IG_ID + "/media")) {
            mediaBodies.add(URLDecoder.decode(body, StandardCharsets.UTF_8));
            reply = "{\"id\":\"c-" + containers.incrementAndGet() + "\"}";
        } else if (path.startsWith("/ig-api-upload/")) {
            reply = "{\"success\":true,\"message\":\"Upload successful.\"}";
        } else if (path.startsWith("/v23.0/c-")) {
            reply = "{\"status_code\":\"FINISHED\"}";
        } else if (path.equals("/v23.0/" + IG_ID + "/media_publish")) {
            reply = "{\"id\":\"m-" + published.incrementAndGet() + "\"}";
        } else if (path.startsWith("/v23.0/m-")) {
            reply = "{\"permalink\":\"https://www.instagram.com/reel/" + path.substring(path.lastIndexOf('/') + 1) + "/\"}";
        } else {
            code = 400;
            reply = "{\"error\":{\"message\":\"Unsupported get request\",\"code\":100}}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    @Order(1)
    void claudeProposesItAndRaulApprovesItIntoProduction() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        material = Files.createDirectories(drive.resolve("DeLito/material"));
        generated = Files.createDirectories(drive.resolve("DeLito/generados"));
        page = material.resolve("pagina 58.png");
        BufferedImage image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(180, 60, 40), 1600, 1200, new Color(250, 230, 200)));
        g.fillRect(0, 0, 1600, 1200);
        g.dispose();
        ImageIO.write(image, "png", page.toFile());
        page = page.toRealPath();
        at = LocalDateTime.of(2026, 11, 16, 19, 0).atZone(FlowClock.MADRID).toInstant();

        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"MATERIAL\",\"location\":" + f.q(material) + "},{\"role\":\"GENERATED\",\"location\":" + f.q(generated) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/instagram/key", "{\"key\":\"" + KEY + "\"}"));
        f.ok(f.put("/api/brands/el-delito/instagram-account", "{\"ig_user_id\":\"" + IG_ID + "\"}"));

        String task = f.order("poemario", "WEEKLY_PROPOSALS", "Propón la semana", "2026-11-16");
        f.complete(task, """
                {"proposals":[{"scheduled_on":"2026-11-16","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Reel",
                  "title":"Leer en voz alta","idea":"Un verso al oído","why":"Los reels van bien"}],"summary":"una"}""");
        assertThat(f.status("poemario", ID)).isEqualTo("PROPOSAL");
        f.ok(f.post("/api/publications/" + ID + "/status-changes", "{\"status\":\"IN_PRODUCTION\"}"));
        assertThat(f.status("poemario", ID)).isEqualTo("IN_PRODUCTION");
    }

    @Test
    @Order(2)
    void theSpecGeneratesItsReelAndClaudeWritesItsText() throws Exception {
        Flow f = flow();
        String task = f.order("poemario", "WEEKLY_SPEC", "spec del lote", ID);
        String spec = """
                {"out":%s,"piezas":[{"tipo":"reel","nombre":"reel_vida","publicacion":"%s","img":%s,
                  "gancho":"Frase de tres segundos","versos":["verso uno"],"final":["El DeLito","un poema de Lito"]}]}"""
                .formatted(f.q(generated), ID, f.q(page));
        f.complete(task, "{\"spec\":" + spec + ",\"summary\":\"1 pieza\"}");
        String pieces = f.task(task).get("result").asString().replaceAll(".*\\(tarea ([0-9a-f-]+)\\).*", "$1");
        f.waitFor(() -> "COMPLETED".equals(f.task(pieces).get("status").asString()), "reel generado", 240);
        assertThat(Files.size(generated.resolve("reel_vida.mp4"))).isPositive();
        JsonNode files = f.read("/api/publications/" + ID + "/files").get("items");
        assertThat(files).hasSize(2); // el vídeo y su portada
        assertThat(files.get(0).get("origin").asString()).isEqualTo("SPEC");

        String captions = f.order("poemario", "CAPTIONS", "textos", ID);
        f.complete(captions, """
                {"publications":[{"id":"%s","caption":"Un verso para decir en voz alta.","hashtags":"#eldelito #poesia"}],"summary":"ok"}"""
                .formatted(ID));
        assertThat(f.publication("poemario", ID).get("caption").asString()).isEqualTo("Un verso para decir en voz alta.");
        assertThat(f.status("poemario", ID)).isEqualTo("IN_PRODUCTION");
    }

    @Test
    @Order(3)
    void itPassesReviewAndQualityAndIsApprovedWithoutAcceptingAnything() throws Exception {
        Flow f = flow();
        for (String next : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            var response = f.post("/api/publications/" + ID + "/status-changes", "{\"status\":\"" + next + "\"}").andReturn().getResponse();
            assertThat(response.getStatus()).as(next + ": " + response.getContentAsString() + " " + f.read("/api/publications/" + ID + "/quality")).isEqualTo(200);
        }
        assertThat(f.status("poemario", ID)).isEqualTo("APPROVED");
        assertThat(f.read("/api/publications/" + ID + "/quality").get("issues")).isEmpty();
    }

    @Test
    @Order(4)
    void itIsScheduledByTheOfficialApiAndPublishedAtItsTimeWithItsLink() throws Exception {
        Flow f = flow();
        clock.set(at.minus(Duration.ofHours(7)));
        calls.clear();
        f.ok(f.post("/api/publications/" + ID + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        JsonNode job = f.job(ID);
        assertThat(job.get("route").asString()).isEqualTo("API oficial");
        assertThat(job.get("status").asString()).isEqualTo("SCHEDULED");
        // Meta no programa: al programarla solo se comprueba la cuenta
        assertThat(calls).containsExactly("GET /v23.0/" + IG_ID);

        clock.set(at.plus(Duration.ofMinutes(1)));
        f.waitFor(() -> "PUBLISHED".equals(f.status("poemario", ID)), "publicada por la API oficial", 60);
        publishedAt = clock.instant();
        job = f.job(ID);
        assertThat(job.get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(job.get("external_id").asString()).isEqualTo("m-1");
        assertThat(job.get("url").asString()).isEqualTo("https://www.instagram.com/reel/m-1/");
        assertThat(f.publication("poemario", ID).get("confirmation").asString()).contains("instagram.com/reel/m-1");
        assertThat(mediaBodies).hasSize(1);
        assertThat(mediaBodies.getFirst()).contains("media_type=REELS").contains("Un verso para decir en voz alta.")
                .contains("#eldelito #poesia");
        assertThat(published.get()).isEqualTo(1);
    }

    @Test
    @Order(5)
    void sevenDaysLaterItsMetricsAreReadWithTheirSourceAndEnterTheTotals() throws Exception {
        Flow f = flow();
        clock.set(publishedAt.plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        f.waitFor(() -> f.requestsOfType("para-claude-pc", "socialhub.metrics-read").size() == 1, "encargo de métricas");
        JsonNode request = f.requestsOfType("para-claude-pc", "socialhub.metrics-read").getFirst();
        assertThat(request.get("inputs").get("publications").get(0).get("url").asString()).isEqualTo("https://www.instagram.com/reel/m-1/");
        f.complete(request.get("taskId").asString(), """
                {"metrics":[{"publication_id":"%s","views":2400,"reach":1800,"likes":120,"comments":9,"saves":31,"shares":14}],
                 "summary":"ok"}""".formatted(ID));
        JsonNode totals = f.read("/api/promotions/poemario/metrics");
        assertThat(totals.get("measured").asInt()).isEqualTo(1);
        assertThat(totals.get("pending").asInt()).isZero();
        assertThat(totals.get("by_format").get(0).get("type").asString()).isEqualTo("Reel");
        assertThat(totals.get("by_format").get(0).get("views").asDouble()).isEqualTo(2400.0);
        // La misma publicación siguió siendo una sola de principio a fin
        assertThat(f.read("/api/promotions/poemario/publications?limit=500").get("items")).hasSize(1);
        clock.reset();
    }
}
