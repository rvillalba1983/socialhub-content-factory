package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Motor de publicación de extremo a extremo con un reloj que se adelanta a mano: Publer con encargos en
 * {@code para-claude-pc}, comprobación, reintentos, LEEME para publicar a mano, duplicados y cancelación.
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
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
class PublishingEngineTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Reloj de la prueba: empieza ahora y se adelanta a mano. */
    static final MovableClock CLOCK = new MovableClock();
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(10);

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static Path story;

    @Autowired MockMvc mvc;
    @Autowired PublishingEngine engine;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() { return CLOCK; }
    }

    @BeforeAll
    static void folders() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
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
    void setup() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(LocalDate.now(MADRID), DAY.plusDays(30))).andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), """
                {"items":[{"role":"ORDERS","location":%s},{"role":"APPROVED","location":%s}]}"""
                .formatted(q(orders), q(approved))).andExpect(status().isOk());
        // El límite diario no frena los encargos de publicación
        write(put("/api/ai/settings"), "{\"daily_limit\":1,\"models\":[]}").andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void publerSchedulesItAndTheCheckConfirmsIt() throws Exception {
        String id = approvedStory("09:00");
        Instant at = DAY.atTime(9, 0).atZone(MADRID).toInstant();

        // No se puede encargar a mano
        write(post("/api/promotions/poemario/ai-orders"),
                "{\"type\":\"PUBLER_SCHEDULE\",\"goal\":\"x\",\"inputs\":[\"" + id + "\"]}").andExpect(status().isBadRequest());

        schedule(id);
        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("WORKING");
        assertThat(job.get("route").asString()).isEqualTo("Publer");
        String task = job.get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");

        // Va a para-claude-pc, con la hora límite 8 h antes y lo que hace falta para programarla
        JsonNode request = pcRequest(task);
        assertThat(request.get("type").asString()).isEqualTo("socialhub.publer-schedule");
        assertThat(Instant.parse(request.get("deadline").asString())).isEqualTo(at.minus(Duration.ofHours(8)));
        assertThat(request.get("inputs").get("publication").get("scheduled_at").asString()).isEqualTo(at.toString());
        assertThat(request.get("inputs").get("publication").get("files").get(0).asString()).isEqualTo(story.toString());
        assertThat(Files.exists(orders.resolve("para-claude"))
                ? list(orders.resolve("para-claude")).stream().noneMatch(p -> p.getFileName().toString().startsWith(task)) : true)
                .isTrue();
        assertThat(get("/api/ai/settings").get("used_today").asLong()).isZero();

        // Quedó a otra hora: no vale
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(at.plusSeconds(3600))));
        waitFor(() -> "FAILED".equals(task(task).get("status").asString()), "hora equivocada");
        assertThat(task(task).get("error").asString()).contains("otra hora");
        // … y se sigue: Publora y la API oficial no están configuradas, así que reintento en una hora
        job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("RETRY_WAIT");
        assertThat(job.get("log").toString()).contains("Publora: no configurada").contains("API oficial: no configurada");

        CLOCK.forward(Duration.ofMinutes(61));
        assertThat(engine.tick()).isEqualTo(1);
        String retry = job(id).get("task_id").asString();
        assertThat(retry).isNotEqualTo(task);
        waitFor(() -> "WAITING".equals(task(retry).get("status").asString()), "segundo encargo");
        result(retry, completed(retry, "{\"scheduled\":true,\"scheduled_for\":\"%s\",\"publer_id\":\"p-77\"}"
                .formatted(DAY.atTime(9, 0).atZone(MADRID).toOffsetDateTime())));
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "programada en Publer");
        assertThat(job(id).get("external_id").asString()).isEqualTo("p-77");
        assertThat(task(retry).get("result").asString()).startsWith("Programada en Publer");

        // A la hora pasa a «Publicando»; 15 minutos después se encarga la comprobación
        CLOCK.set(at.plusSeconds(60));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHING");
        CLOCK.set(at.plus(Duration.ofMinutes(16)));
        engine.tick();
        job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("CHECKING");
        String check = job.get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(check).get("status").asString()), "comprobación enviada");
        assertThat(pcRequest(check).get("type").asString()).isEqualTo("socialhub.publish-check");
        assertThat(pcRequest(check).get("inputs").get("external_id").asString()).isEqualTo("p-77");

        // Sin enlace ni identificador no se da por publicada
        result(check, completed(check, "{\"published\":true}"));
        waitFor(() -> "FAILED".equals(task(check).get("status").asString()), "sin prueba");
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISH_ERROR");
        assertThat(alerts()).anyMatch(a -> a.get("target_type").asString().equals("PUBLICATION")
                && a.get("target_id").asString().equals(id) && a.get("kind").asString().equals("FAILED"));
    }

    @Test
    @Order(3)
    void confirmedWithTheLink() throws Exception {
        CLOCK.reset();
        String id = approvedStory("10:00");
        Instant at = DAY.atTime(10, 0).atZone(MADRID).toInstant();
        schedule(id);
        String task = job(id).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(at)));
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "programada");

        CLOCK.set(at.plus(Duration.ofMinutes(20)));
        engine.tick();
        String check = job(id).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(check).get("status").asString()), "comprobación enviada");
        result(check, completed(check, "{\"published\":true,\"url\":\"https://www.instagram.com/stories/elde_lito/1\"}"));
        waitFor(() -> "PUBLISHED".equals(publication(id).get("status").asString()), "publicada");
        assertThat(job(id).get("url").asString()).isEqualTo("https://www.instagram.com/stories/elde_lito/1");
        assertThat(publication(id).get("confirmation").asString()).contains("instagram.com");
    }

    @Test
    @Order(4)
    void withoutTimeItGoesManualAndALateResultIsAPossibleDuplicate() throws Exception {
        CLOCK.reset();
        String id = approvedStory("11:00");
        Instant at = DAY.atTime(11, 0).atZone(MADRID).toInstant();
        schedule(id);
        String task = job(id).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");

        // Claude no lo hace: 7 h antes se deja de esperar y no da tiempo a reintentar sin bajar de 6 h
        CLOCK.set(at.minus(Duration.ofHours(7)));
        engine.tick();
        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("MANUAL");
        Path leeme = Path.of(job.get("manual_file").asString());
        assertThat(leeme.getParent()).isEqualTo(approved);
        assertThat(Files.readString(leeme)).contains("PUBLICAR A MANO").contains(story.toString()).contains("no lo hizo a tiempo");
        assertThat(publication(id).get("flag").asString()).startsWith("Publicación manual con LEEME");
        assertThat(alerts()).anyMatch(a -> a.get("target_id").asString().equals(id) && a.get("kind").asString().equals("BLOCKED"));

        // El resultado llega tarde: puede haber un duplicado en Publer
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(at)));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "resultado tardío");
        assertThat(publication(id).get("flag").asString()).startsWith("Posible duplicado");
        assertThat(job(id).get("status").asString()).isEqualTo("MANUAL");

        // A mano se comprueba 2 h después de la hora; si no aparece, error
        CLOCK.set(at.plus(Duration.ofMinutes(30)));
        engine.tick();
        assertThat(job(id).get("status").asString()).isEqualTo("MANUAL");
        assertThat(publication(id).get("status").asString()).isEqualTo("PUBLISHING");
        CLOCK.set(at.plus(Duration.ofHours(2)).plusSeconds(60));
        engine.tick();
        String check = job(id).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(check).get("status").asString()), "comprobación enviada");
        result(check, completed(check, "{\"published\":false,\"note\":\"No está en el perfil\"}"));
        waitFor(() -> "PUBLISH_ERROR".equals(publication(id).get("status").asString()), "no aparece");
        assertThat(job(id).get("last_error").asString()).isEqualTo("No está en el perfil");
    }

    @Test
    @Order(5)
    void leavingScheduledStopsItAndWarnsIfPublerHasIt() throws Exception {
        CLOCK.reset();
        String id = approvedStory("12:00");
        Instant at = DAY.atTime(12, 0).atZone(MADRID).toInstant();
        schedule(id);
        String task = job(id).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(at)));
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "programada");

        // Programada no se edita: ni datos ni archivos
        write(put("/api/publications/" + id), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Otra","scheduled_on":"%s","scheduled_time":"12:00"}"""
                .formatted(DAY)).andExpect(status().isConflict());
        write(delete("/api/publications/" + id + "/files").param("path", story.toString()), "").andExpect(status().isConflict());

        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(job(id).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(publication(id).get("flag").asString()).startsWith("Estaba programada en Publer");
        assertThat(alerts()).anyMatch(a -> a.get("target_id").asString().equals(id));

        write(delete("/api/publications/" + id + "/flag"), "").andExpect(status().isNoContent());
        JsonNode cleared = publication(id);
        assertThat(!cleared.has("flag") || cleared.get("flag").isNull()).isTrue();
        assertThat(alerts()).noneMatch(a -> a.get("target_id").asString().equals(id));

        // Al reloj no le toca nada con una cancelada
        CLOCK.set(at.plusSeconds(3600));
        engine.tick();
        assertThat(publication(id).get("status").asString()).isEqualTo("APPROVED");
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

    private JsonNode task(String id) throws Exception { return get("/api/tasks/" + id); }

    private List<JsonNode> alerts() throws Exception {
        List<JsonNode> list = new java.util.ArrayList<>();
        get("/api/factory").get("alerts").forEach(list::add);
        return list;
    }

    private JsonNode publication(String id) throws Exception {
        for (JsonNode p : get("/api/promotions/poemario/publications").get("items")) {
            if (p.get("id").asString().equals(id)) return p;
        }
        throw new AssertionError("No existe " + id);
    }

    private JsonNode pcRequest(String taskId) throws Exception {
        Path file = list(orders.resolve("para-claude-pc")).stream()
                .filter(p -> p.getFileName().toString().startsWith(taskId)).reduce((a, b) -> b).orElseThrow();
        return json.readTree(Files.readString(file));
    }

    private static String completed(String taskId, String outputs) {
        return """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed","outputs":%s,
                 "agent":{"runtime":"tarea programada en el PC","model":"sonnet"}}""".formatted(taskId, outputs);
    }

    private void result(String taskId, String content) throws Exception {
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(taskId + "-" + System.nanoTime() + ".json");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private JsonNode get(String url) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andReturn().getResponse().getContentAsString());
    }

    private static List<Path> list(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        }
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

    /** Reloj que se puede adelantar. */
    static final class MovableClock extends Clock {
        private volatile Duration offset = Duration.ZERO;

        void forward(Duration d) { offset = offset.plus(d); }

        void set(Instant at) { offset = Duration.between(Instant.now(), at); }

        void reset() { offset = Duration.ZERO; }

        @Override public ZoneId getZone() { return MADRID; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return Instant.now().plus(offset); }
    }
}
