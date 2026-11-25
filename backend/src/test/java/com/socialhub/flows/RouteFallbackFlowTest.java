package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Flujo «Cadena de vías» (manual, §6), de extremo a extremo y con el motor en marcha por su cuenta: una publicación
 * programada baja por las vías con el motivo de cada salto. Publer es Claude con Chrome (se simula con el archivo de
 * resultado) y Publora un servidor HTTP local; la API oficial de Instagram no está configurada.
 * <ul>
 *   <li>Publer falla (Claude no puede) → Publora responde con un error por HTTP → reintento a la hora → a menos de 7 h
 *       Publer ya no da tiempo → Publora vuelve a fallar → LEEME para publicar a mano, con el motivo de cada vía.</li>
 *   <li>Con Publora ya bien: Publer falla → Publora la programa una sola vez → la comprobación la da por publicada.</li>
 * </ul>
 */
@FlowTest
@TestPropertySource(properties = {
        "socialhub.publishing.initial-delay-millis=200",
        "socialhub.publishing.tick-millis=300"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RouteFallbackFlowTest {

    static final String KEY = "sk_123.abcdef0123456789";

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static Path story;
    static HttpServer publora;
    /** Lo que ha recibido el Publora de mentira: «MÉTODO ruta». */
    static final List<String> calls = new CopyOnWriteArrayList<>();
    static volatile boolean down = true;
    static volatile String postStatus = "scheduled";

    @Autowired MockMvc mvc;
    /** Estas pruebas se escribieron con el margen de 6 horas de antes del ajuste: lo fijan para conservar sus horarios. */
    @Autowired com.socialhub.settings.PublishSettings margin;
    @org.junit.jupiter.api.BeforeEach void sixHourMargin() { margin.update(360, 360); }
    @org.junit.jupiter.api.AfterEach void defaultMargin() { margin.update(com.socialhub.settings.PublishSettings.DEFAULT_MINUTES, com.socialhub.settings.PublishSettings.TIKTOK_DEFAULT_MINUTES); }
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        publora = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        publora.createContext("/", RouteFallbackFlowTest::handle);
        publora.start();
        registry.add("socialhub.publora.base-url", () -> "http://127.0.0.1:" + publora.getAddress().getPort() + "/api/v1");
    }

    @AfterAll
    static void stop() { publora.stop(0); }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        ex.getRequestBody().readAllBytes();
        if (!path.startsWith("/upload/")) calls.add(ex.getRequestMethod() + " " + path);
        String reply;
        int code = 200;
        if (path.startsWith("/upload/")) {
            reply = "";
        } else if (!KEY.equals(ex.getRequestHeaders().getFirst("x-publora-key"))) {
            code = 401;
            reply = "{\"error\":\"Invalid API key\"}";
        } else if (path.equals("/api/v1/platform-connections")) {
            reply = """
                    {"success":true,"connections":[
                      {"platformId":"instagram-111","username":"elde_lito","displayName":"El DeLito","connectionStatus":"active"}]}""";
        } else if (path.equals("/api/v1/create-post")) {
            if (down) {
                code = 503;
                reply = "{\"error\":\"Servicio no disponible\",\"code\":\"UNAVAILABLE\"}";
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

    @Test
    @Order(1)
    void setup() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        BufferedImage image = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1080, 1920);
        g.dispose();
        story = approved.resolve("historia.jpg");
        ImageIO.write(image, "jpg", story.toFile());

        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(approved) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/brands/el-delito/publer-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"account_name\":\"El DeLito\"}]}"));
        f.ok(f.put("/api/publora/key", "{\"key\":\"" + KEY + "\"}"));
        f.ok(f.put("/api/brands/el-delito/publora-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"connection_id\":\"instagram-111\"}]}"));
        // La API oficial de Instagram no está configurada: es la primera vía y siempre cede
        calls.clear();
    }

    @Test
    @Order(2)
    void whenPublerAndPubloraFailItGoesThroughEachRouteWithItsReasonAndEndsInTheManualReadme() throws Exception {
        Flow f = flow();
        down = true;
        String id = approvedStory("2026-11-12", "09:00");
        Instant at = LocalDateTime.of(2026, 11, 12, 9, 0).atZone(FlowClock.MADRID).toInstant();

        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        // 1. Publer: encargo a Claude con Chrome
        assertThat(f.job(id).get("route").asString()).isEqualTo("Publer");
        String first = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(first).get("status").asString()), "encargo de Publer enviado");
        assertThat(f.pcRequest(first).get("type").asString()).isEqualTo("socialhub.publer-schedule");

        // Claude no consigue programarla: se sigue por Publora, que responde 503 por HTTP
        f.result(first, failedResult(first, "Chrome no pudo abrir Publer"));
        f.waitFor(() -> "RETRY_WAIT".equals(f.job(id).get("status").asString()), "reintento en una hora");
        String log = f.job(id).get("log").toString();
        assertThat(log).contains("Chrome no pudo abrir Publer").contains("Publora").contains("Servicio no disponible")
                .contains("API oficial: no configurada");
        assertThat(calls).containsExactly("POST /api/v1/create-post");
        assertThat(f.status("poemario", id)).isEqualTo("SCHEDULED");

        // 2. Pasada la hora de espera se reintenta desde Publer, con un encargo nuevo
        clock.set(clock.instant().plus(Duration.ofMinutes(61)));
        f.waitFor(() -> "WORKING".equals(f.job(id).get("status").asString())
                && !first.equals(f.job(id).get("task_id").asString()), "segundo encargo de Publer");
        String retry = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(retry).get("status").asString()), "segundo encargo enviado");
        f.result(retry, failedResult(retry, "Publer pide volver a entrar"));
        f.waitFor(() -> "RETRY_WAIT".equals(f.job(id).get("status").asString()), "otra vez en espera");
        assertThat(calls).containsExactly("POST /api/v1/create-post", "POST /api/v1/create-post");

        // 3. A menos de 7 h Publer ya no da tiempo; Publora vuelve a fallar y no queda margen para otra ronda: a mano
        clock.set(at.minus(Duration.ofHours(7)));
        f.waitFor(() -> "MANUAL".equals(f.job(id).get("status").asString()), "LEEME manual");
        JsonNode job = f.job(id);
        assertThat(job.get("log").toString()).contains("Publer: ya no da tiempo").contains("Servicio no disponible")
                .contains("API oficial: no configurada");
        Path leeme = Path.of(job.get("manual_file").asString());
        assertThat(Files.isSameFile(leeme.getParent(), approved)).isTrue();
        assertThat(Files.readString(leeme)).contains("PUBLICAR A MANO").contains(story.toRealPath().toString());
        assertThat(f.publication("poemario", id).get("flag").asString()).startsWith("Publicación manual con LEEME");
        assertThat(f.alerts()).anyMatch(a -> a.get("target_id").asString().equals(id) && a.get("kind").asString().equals("BLOCKED"));

        // Sin duplicados: nada se programó en ninguna vía, solo hubo intentos de Publora que fallaron al crear
        assertThat(calls).containsOnly("POST /api/v1/create-post").hasSize(3);
        assertThat(f.requestsOfType("para-claude-pc", "socialhub.publer-schedule")).hasSize(2);
    }

    @Test
    @Order(3)
    void whenPubloraRespondsAgainItSchedulesOnceAndTheCheckConfirmsIt() throws Exception {
        Flow f = flow();
        clock.reset();
        down = false;
        calls.clear();
        String id = approvedStory("2026-11-13", "09:00");
        Instant at = LocalDateTime.of(2026, 11, 13, 9, 0).atZone(FlowClock.MADRID).toInstant();

        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        String task = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "encargo de Publer enviado");
        f.result(task, failedResult(task, "Chrome no está abierto"));
        f.waitFor(() -> "SCHEDULED".equals(f.job(id).get("status").asString()), "programada en Publora");
        JsonNode job = f.job(id);
        assertThat(job.get("route").asString()).isEqualTo("Publora");
        assertThat(job.get("external_id").asString()).isEqualTo("g-1");
        assertThat(job.get("log").toString()).contains("Chrome no está abierto").contains("API oficial: no configurada");
        assertThat(calls).containsExactly("POST /api/v1/create-post", "POST /api/v1/get-upload-url",
                "POST /api/v1/complete-media/m-1", "PUT /api/v1/update-post/g-1");

        // Un rato después no se programa de nuevo en ninguna vía
        Thread.sleep(1_000);
        assertThat(calls).hasSize(4);
        assertThat(f.requestsOfType("para-claude-pc", "socialhub.publer-schedule")).hasSize(3);

        // A la hora pasa a «Publicando» y la comprobación por la API de Publora la da por publicada con su enlace
        clock.set(at.plus(Duration.ofMinutes(16)));
        f.waitFor(() -> "PUBLISHING".equals(f.status("poemario", id)) || "CHECKING".equals(f.job(id).get("status").asString()),
                "publicando");
        postStatus = "published";
        clock.set(at.plus(Duration.ofMinutes(25)));
        f.waitFor(() -> "PUBLISHED".equals(f.status("poemario", id)), "publicada");
        assertThat(f.job(id).get("url").asString()).isEqualTo("https://www.instagram.com/stories/elde_lito/1789");
        clock.reset();
    }

    @Test
    @Order(4)
    void takingThePublicationOutOfScheduledRemovesTheRealPendingFileAndALateResultWarnsToDeleteItInPubler() throws Exception {
        Flow f = flow();
        clock.reset();
        down = true;
        String id = approvedStory("2026-11-14", "09:00");

        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        String task = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "encargo de Publer enviado");
        Path file = Flow.list(orders.resolve("para-claude-pc")).stream()
                .filter(p -> p.getFileName().toString().startsWith(task)).findFirst().orElseThrow();
        assertThat(file).exists();

        // Raúl la saca de «Programada»: el archivo se retira de verdad de para-claude-pc y la tarea no queda «Cancelada por ti»
        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}"));
        f.waitFor(() -> "CANCELLED".equals(f.task(task).get("status").asString()), "encargo retirado");
        assertThat(file).doesNotExist();
        assertThat(f.task(task).get("reason").asString()).isEqualTo("Retirada porque la publicación ya no está programada");

        // Claude ya lo había empezado y lo termina: el resultado no se descarta, avisa de que hay que borrarla en Publer
        f.result(task, Flow.completed(task, "{\"scheduled\":true,\"scheduled_for\":\"2026-11-14T09:00:00+01:00\"}"));
        f.waitFor(() -> f.publication("poemario", id).path("flag").asString("").contains("Revisa Publer y bórrala allí"),
                "aviso de resultado tardío");
    }

    // ---------------------------------------------------------------- utilidades

    private String approvedStory(String day, String time) throws Exception {
        Flow f = flow();
        JsonNode created = f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Historia","title":"Historia %s","scheduled_on":"%s",
                 "scheduled_time":"%s","initial_status":"IN_PRODUCTION"}""".formatted(day, day, time)));
        String id = created.get("id").asString();
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(story) + "}"));
        for (String s : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"" + s + "\"}"));
        }
        return id;
    }

    private static String failedResult(String taskId, String error) {
        return """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"failed","outputs":{},
                 "error":"%s","agent":{"runtime":"tarea programada en el PC"}}""".formatted(taskId, error);
    }
}
