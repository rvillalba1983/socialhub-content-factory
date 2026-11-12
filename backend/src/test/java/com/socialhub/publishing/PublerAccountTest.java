package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Cuenta de Publer por marca y red, de extremo a extremo: sin cuenta la publicación queda retenida con su motivo y sale
 * la recomendación; al indicarla se programa sola; la cuenta de TikTok compartida por las dos marcas comparte también el
 * tope de publicaciones programadas (aquí 2, para no repetir diez veces el recorrido; el 10 por defecto lo comprueba
 * {@code PublerRouteTest}).
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
        "socialhub.publishing.initial-delay-millis=3600000",
        "socialhub.publer.scheduled-limit=2"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublerAccountTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final MovableClock CLOCK = new MovableClock();
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(10);
    static final String SHARED = "Lito · Litoques · El DeLito";

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static Path video;
    /** Promoción de cada publicación creada. */
    static final Map<String, String> PROMOTION_OF = new HashMap<>();

    @Autowired MockMvc mvc;
    @Autowired PublishingEngine engine;
    private final JsonMapper json = JsonMapper.builder().build();

    @TestConfiguration
    static class Config {
        @Bean @Primary Clock movableClock() { return CLOCK; }
    }

    @BeforeAll
    static void folders() throws Exception {
        orders = Files.createDirectories(drive.resolve("marcas/encargos"));
        approved = Files.createDirectories(drive.resolve("marcas/aprobados"));
        // Solo importa la extensión: la comprobación de calidad no abre los vídeos
        video = Files.write(approved.resolve("pieza.mp4"), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'});
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        for (var brand : List.of(List.of("El DeLito", "@elde_lito", "#D23A2E", "poemario"), List.of("Litoques", "@litoques", "#2E7DD2", "versos"))) {
            write(post("/api/brands"), "{\"name\":\"%s\",\"handle\":\"%s\",\"color\":\"%s\"}".formatted(brand.get(0), brand.get(1), brand.get(2)));
            String id = brand.get(0).equals("El DeLito") ? "el-delito" : "litoques";
            write(post("/api/promotions"), """
                    {"brand_id":"%s","name":"%s","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                    .formatted(id, brand.get(3), LocalDate.now(MADRID), DAY.plusDays(30))).andExpect(status().isCreated());
            write(put("/api/brands/" + id + "/folders"), """
                    {"items":[{"role":"ORDERS","location":%s},{"role":"APPROVED","location":%s}]}"""
                    .formatted(q(orders), q(approved))).andExpect(status().isOk());
        }
    }

    @Test
    @Order(2)
    void withoutTheAccountThePublicationStaysHeldWithItsReasonAndARecommendationAppears() throws Exception {
        String id = approvedTikTok("versos", "09:00");
        schedule(id);

        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("HELD");
        assertThat(job.get("status_label").asString()).isEqualTo("Retenida");
        assertThat(job.get("last_error").asString()).isEqualTo("Falta la cuenta de Publer de Litoques para TikTok");
        assertThat(job.has("task_id") && !job.get("task_id").isNull()).isFalse();
        assertThat(publication(id).get("flag").asString()).isEqualTo("Retenida: Falta la cuenta de Publer de Litoques para TikTok");
        // No se ha creado ningún encargo
        assertThat(Files.exists(orders.resolve("para-claude-pc")) ? list(orders.resolve("para-claude-pc")) : List.of()).isEmpty();

        JsonNode recommendation = recommendation("Indica la cuenta de Publer de Litoques para TikTok");
        assertThat(recommendation.get("kind").asString()).isEqualTo("PUBLER_ACCOUNT_MISSING");
        assertThat(recommendation.get("target").get("screen").asString()).isEqualTo("PUBLER");
        assertThat(recommendation.get("target").get("brand_id").asString()).isEqualTo("litoques");

        // Sigue retenida mientras falte la cuenta
        CLOCK.forward(Duration.ofMinutes(6));
        engine.tick();
        assertThat(job(id).get("status").asString()).isEqualTo("HELD");
        assertThat(job(id).get("log").toString().split("retenida", -1).length - 1).as(job(id).get("log").toString()).isEqualTo(1); // el motivo no se repite en el historial
    }

    @Test
    @Order(3)
    void whenTheAccountIsGivenItIsScheduledAloneWithTheTikTokOptionsAndTheRecommendationGoes() throws Exception {
        String id = DAY + "-tiktok";
        write(put("/api/brands/litoques/publer-accounts"), "{\"items\":[{\"platform\":\"TIKTOK\",\"account_name\":\"  %s \"}]}".formatted(SHARED))
                .andExpect(status().isOk());
        assertThat(get("/api/brands/litoques/publer-accounts").get("items").get(0).get("account_name").asString()).isEqualTo(SHARED);
        assertThat(recommendations().toString()).doesNotContain("Indica la cuenta de Publer");

        CLOCK.forward(Duration.ofMinutes(6));
        engine.tick();
        JsonNode job = job(id);
        assertThat(job.get("status").asString()).isEqualTo("WORKING");
        String task = job.get("task_id").asString();
        assertThat(publication(id).has("flag") && !publication(id).get("flag").isNull()).isFalse();
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");

        JsonNode inputs = pcRequest(task).get("inputs");
        assertThat(inputs.get("publer_account").asString()).isEqualTo(SHARED);
        String how = inputs.get("how").toString();
        assertThat(how).contains("en la cuenta «" + SHARED + "» de Publer").contains("«Todo el mundo»")
                .contains("Comentarios, Dúo y Stitch").contains("los sonidos ya van en el vídeo");

        Instant at = DAY.atTime(9, 0).atZone(MADRID).toInstant();
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(at)));
        waitFor(() -> "SCHEDULED".equals(job(id).get("status").asString()), "programada en Publer");
    }

    @Test
    @Order(4)
    void theOtherBrandSharesTheTikTokAccountAndItsLimit() throws Exception {
        CLOCK.reset();
        write(put("/api/brands/el-delito/publer-accounts"), "{\"items\":[{\"platform\":\"TIKTOK\",\"account_name\":\"%s\"}]}".formatted(SHARED.toLowerCase()))
                .andExpect(status().isOk());

        // La segunda (de El DeLito) cabe: con la de Litoques hacen 2, el tope de esta prueba
        String second = approvedTikTok("poemario", "10:00");
        schedule(second);
        String task = job(second).get("task_id").asString();
        assertThat(job(second).get("status").asString()).isEqualTo("WORKING");
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");
        assertThat(pcRequest(task).get("inputs").get("publer_account").asString()).isEqualTo(SHARED.toLowerCase());
        result(task, completed(task, "{\"scheduled\":true,\"scheduled_for\":\"%s\"}".formatted(DAY.atTime(10, 0).atZone(MADRID).toInstant())));
        waitFor(() -> "SCHEDULED".equals(job(second).get("status").asString()), "programada");

        // La tercera, de cualquiera de las dos marcas, queda en espera con el motivo (la cuenta se compara sin mayúsculas)
        String third = approvedTikTok("versos", "20:00");
        schedule(third);
        JsonNode held = job(third);
        assertThat(held.get("status").asString()).isEqualTo("HELD");
        assertThat(held.get("last_error").asString()).contains("«" + SHARED + "»").contains("2 publicaciones programadas").contains("límite del plan gratuito");
        assertThat(publication(third).get("flag").asString()).startsWith("Retenida: ");
        assertThat(recommendations().toString()).doesNotContain("Indica la cuenta de Publer"); // no falta la cuenta, es el límite

        // Mientras las dos siguen programadas, sigue en espera
        CLOCK.forward(Duration.ofMinutes(6));
        engine.tick();
        assertThat(job(third).get("status").asString()).isEqualTo("HELD");

        // Sale la primera de las dos (su hora pasa): queda un hueco y la tercera se programa sola
        CLOCK.set(DAY.atTime(9, 1).atZone(MADRID).toInstant());
        engine.tick(); // la primera pasa a «Publicando» y la tercera se vuelve a probar
        waitFor(() -> {
            engine.tick();
            return "WORKING".equals(job(third).get("status").asString()) && job(third).has("task_id");
        }, "la tercera se programa al quedar un hueco");
    }

    @Test
    @Order(5)
    void takingAHeldOneOutOfScheduledCancelsItAndLeavesNoFlag() throws Exception {
        CLOCK.reset();
        write(put("/api/brands/el-delito/publer-accounts"), "{\"items\":[]}").andExpect(status().isOk());
        String id = approvedTikTok("poemario", DAY.plusDays(3), "18:00");
        schedule(id);
        assertThat(job(id).get("status").asString()).isEqualTo("HELD");
        assertThat(publication(id).get("flag").asString()).startsWith("Retenida: ");

        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(job(id).get("status").asString()).isEqualTo("CANCELLED");
        JsonNode p = publication(id);
        assertThat(!p.has("flag") || p.get("flag").isNull()).isTrue();
    }

    @Test
    @Order(6)
    void heldUntilTheLimitOfPublerThenItFallsToTheOtherRoutesBothForAMissingAccountAndAFullPlan() throws Exception {
        CLOCK.reset();
        // Sin cuenta (El DeLito no tiene ninguna)
        String missing = approvedTikTok("poemario", DAY.plusDays(4), "22:00");
        schedule(missing);
        assertThat(job(missing).get("status").asString()).isEqualTo("HELD");
        // Con la cuenta, pero con el plan lleno (las programadas de antes siguen contando con el reloj al día de hoy)
        String full = approvedTikTok("versos", DAY.plusDays(5), "22:30");
        schedule(full);
        assertThat(job(full).get("status").asString()).isEqualTo("HELD");
        assertThat(job(full).get("last_error").asString()).contains("límite del plan gratuito");

        // 7 h antes de la primera Publer ya no llega: sin Publora ni API oficial no hay vía y, sin margen para reintentar, a mano
        CLOCK.set(DAY.plusDays(4).atTime(22, 0).atZone(MADRID).toInstant().minus(Duration.ofHours(7)).plusSeconds(60));
        engine.tick();
        JsonNode job = job(missing);
        assertThat(job.get("status").asString()).isEqualTo("MANUAL");
        assertThat(job.get("log").toString()).contains("Publer: ya no da tiempo");
        assertThat(publication(missing).get("flag").asString()).startsWith("Publicación manual con LEEME");

        CLOCK.set(DAY.plusDays(5).atTime(22, 30).atZone(MADRID).toInstant().minus(Duration.ofHours(7)).plusSeconds(60));
        engine.tick();
        assertThat(job(full).get("status").asString()).isEqualTo("MANUAL");
        assertThat(job(full).get("log").toString()).contains("Publer: ya no da tiempo");
        CLOCK.reset();
    }

    // ---------------------------------------------------------------- utilidades

    private String approvedTikTok(String promotion, String time) throws Exception {
        return approvedTikTok(promotion, DAY, time);
    }

    private String approvedTikTok(String promotion, LocalDate day, String time) throws Exception {
        String body = write(post("/api/promotions/" + promotion + "/publications"), """
                {"platform":"TIKTOK","type":"Vídeo","title":"TikTok %s","scheduled_on":"%s",
                 "scheduled_time":"%s","initial_status":"IN_PRODUCTION","caption":"Un verso nuevo de las %s del %s","hashtags":"#poesia"}""".formatted(time, day, time, time, day))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asString();
        PROMOTION_OF.put(id, promotion);
        write(post("/api/publications/" + id + "/files"), "{\"path\":" + q(video) + "}").andExpect(status().isCreated());
        for (String s : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            var r = write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"" + s + "\"}").andReturn().getResponse();
            assertThat(r.getStatus()).as(s + ": " + r.getContentAsString()).isEqualTo(200);
        }
        return id;
    }

    private void schedule(String id) throws Exception {
        write(post("/api/publications/" + id + "/status-changes"), "{\"status\":\"SCHEDULED\"}").andExpect(status().isOk());
    }

    private JsonNode job(String id) throws Exception { return get("/api/publications/" + id + "/publish-job"); }

    private JsonNode task(String id) throws Exception { return get("/api/tasks/" + id); }

    private JsonNode recommendations() throws Exception { return get("/api/factory").get("recommendations"); }

    private JsonNode recommendation(String title) throws Exception {
        for (JsonNode r : recommendations()) {
            if (r.get("title").asString().equals(title)) return r;
        }
        throw new AssertionError("No hay recomendación «" + title + "»: " + recommendations());
    }

    private JsonNode publication(String id) throws Exception {
        for (JsonNode p : get("/api/promotions/" + PROMOTION_OF.get(id) + "/publications").get("items")) {
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
            return new ArrayList<>(s.filter(Files::isRegularFile).sorted().toList());
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
