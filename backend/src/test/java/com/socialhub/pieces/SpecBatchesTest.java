package com.socialhub.pieces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
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
 * Spec por lotes: cuándo toca pedirlo (3 o más pendientes, o la más próxima en menos de 3 días), que una publicación no
 * entre en dos specs abiertos y que un spec cancelado o fallido del todo libere sus publicaciones.
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.ai.retry-initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpecBatchesTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final LocalDate TODAY = LocalDate.now(MADRID);

    @TempDir static Path drive;

    @Autowired MockMvc mvc;
    @Autowired WeeklySpecHandler handler;
    @Autowired AiOrders orders;
    @Autowired com.socialhub.production.ProductionService production;
    private final JsonMapper json = JsonMapper.builder().build();

    private static String id(int daysAhead) { return TODAY.plusDays(daysAhead) + "-instagram"; }

    private List<String> batch(String promotion) { return handler.autoOrderBatch(promotion, Instant.now()); }

    private void publication(String promotion, int daysAhead, String status) throws Exception {
        write(post("/api/promotions/" + promotion + "/publications"), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Publicación %d","scheduled_on":"%s",
                 "scheduled_time":"13:00","initial_status":"%s"}""".formatted(daysAhead, TODAY.plusDays(daysAhead), status))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        Path orderFolder = Files.createDirectories(drive.resolve("encargos"));
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + q(orderFolder) + "}]}")
                .andExpect(status().isOk());
        write(put("/api/ai/settings"), "{\"daily_limit\":100,\"models\":[]}").andExpect(status().isOk());
        for (String name : List.of("Poemario", "Cuaderno", "Vacia")) {
            write(post("/api/promotions"), """
                    {"brand_id":"el-delito","name":"%s","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                    .formatted(name, TODAY.minusDays(1), TODAY.plusDays(90))).andExpect(status().isCreated());
        }
    }

    @Test
    @Order(2)
    void withoutPendingPublicationsNothingIsAskedAndASpecWithoutPublicationsCannotBeOrdered() throws Exception {
        assertThat(batch("vacia")).isEmpty();
        assertThatThrownBy(() -> orders.submit("vacia", AiRequestType.WEEKLY_SPEC, "spec", "spec", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No hay publicaciones pendientes");
        // Ni las ideas, propuestas o publicaciones que ya tienen piezas o están más allá del borrador cuentan como pendientes
        publication("vacia", 40, "PROPOSAL");
        publication("vacia", 41, "IDEA");
        assertThat(batch("vacia")).isEmpty();
    }

    @Test
    @Order(3)
    void twoPublicationsFarAwayDoNotAskAndTheThirdOneDoes() throws Exception {
        publication("poemario", 10, "IN_PRODUCTION");
        publication("poemario", 11, "IN_PRODUCTION");
        assertThat(batch("poemario")).isEmpty();
        publication("poemario", 12, "IN_PRODUCTION");
        assertThat(batch("poemario")).containsExactly(id(10), id(11), id(12));
    }

    @Test
    @Order(4)
    void oneOrTwoPublicationsAskWhenTheClosestOneIsLessThanThreeDaysAway() throws Exception {
        publication("cuaderno", 2, "IN_PRODUCTION");
        assertThat(batch("cuaderno")).containsExactly(id(2));
        publication("cuaderno", 30, "IN_PRODUCTION");
        // El spec lleva todas las pendientes, también la lejana
        assertThat(batch("cuaderno")).containsExactly(id(2), id(30));
    }

    @Test
    @Order(5)
    void aPublicationNeverEntersTwoOpenSpecsAndACancelledSpecReleasesItsPublications() throws Exception {
        String task = orders.submit("poemario", AiRequestType.WEEKLY_SPEC, "Spec", "Spec", batch("poemario"));
        // Con el spec abierto no hay nada pendiente
        assertThat(batch("poemario")).isEmpty();
        // Otra publicación cercana: se pide sola, sin repetir las del spec abierto
        publication("poemario", 1, "IN_PRODUCTION");
        assertThat(batch("poemario")).containsExactly(id(1));
        // A mano tampoco se pueden repetir
        assertThatThrownBy(() -> orders.submit("poemario", AiRequestType.WEEKLY_SPEC, "Otra", "Otra", List.of(id(10))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ya está en otro spec");
        // Un encargo a mano sin datos lleva las pendientes de ahora, sin las que ya están en el spec abierto
        String manual = orders.submit("poemario", AiRequestType.WEEKLY_SPEC, "A mano", "A mano", List.of());
        assertThat(batch("poemario")).isEmpty();
        command(manual, "CANCEL");
        assertThat(batch("poemario")).containsExactly(id(1));
        // Cancelado el spec, sus publicaciones vuelven a contar
        command(task, "CANCEL");
        assertThat(batch("poemario")).containsExactly(id(1), id(10), id(11), id(12));
    }

    @Test
    @Order(6)
    void aSpecThatFailsForGoodReleasesItsPublicationsButNotWhileItCanStillBeRetried() throws Exception {
        List<String> ids = batch("poemario");
        String task = orders.submit("poemario", AiRequestType.WEEKLY_SPEC, "Spec", "Spec", ids);
        assertThat(batch("poemario")).isEmpty();
        for (int attempt = 1; attempt <= 3; attempt++) {
            int n = attempt;
            waitFor(() -> "WAITING".equals(task(task).get("status").asString()) && task(task).get("attempts").asInt() == n,
                    "intento " + n + " enviado");
            fail(task, attempt);
            waitFor(() -> "FAILED".equals(task(task).get("status").asString()), "intento " + n + " fallido");
            if (attempt < 3) {
                // Falla pero quedan intentos: sigue ocupando sus publicaciones
                assertThat(batch("poemario")).isEmpty();
                command(task, "RETRY");
            }
        }
        // Sin intentos: libera sus publicaciones, pero ese mismo lote no se pide solo otra vez durante 24 h
        assertThat(batch("poemario")).isEmpty();
        // A mano sí se puede
        String manual = orders.submit("poemario", AiRequestType.WEEKLY_SPEC, "A mano", "A mano", ids);
        command(manual, "CANCEL");
        // Un lote distinto (una publicación más) no está bloqueado
        publication("poemario", 13, "IN_PRODUCTION");
        assertThat(batch("poemario")).containsExactly(id(1), id(10), id(11), id(12), id(13));
    }

    private List<String> pendingIds(String promotion) {
        return handler.pending(promotion, Instant.now()).stream().map(com.socialhub.publishing.PublishingQueries.AwaitingContent::id).toList();
    }

    @Test
    @Order(7)
    void anOldSpecWithADateCoversThePublicationsOfThatWeekAndACompletedOneKeepsCoveringThem() throws Exception {
        assertThat(pendingIds("cuaderno")).containsExactly(id(2), id(30));
        // Un spec antiguo llevaba una fecha como dato: cubre las publicaciones de su semana (de lunes a domingo)
        String legacy = production.submitAi(new com.socialhub.production.TaskRequest(AiRequestType.WEEKLY_SPEC.stationId(),
                "cuaderno", AiRequestType.WEEKLY_SPEC.taskType(), "Spec antiguo", "Spec antiguo",
                List.of(TODAY.plusDays(2).toString()), List.of()), "SONNET");
        assertThat(pendingIds("cuaderno")).containsExactly(id(30));
        command(legacy, "CANCEL");
        assertThat(pendingIds("cuaderno")).containsExactly(id(2), id(30));

        // Un spec completado sigue cubriendo sus publicaciones (mientras se generan las piezas no se vuelve a pedir)
        String task = orders.submit("cuaderno", AiRequestType.WEEKLY_SPEC, "Spec", "Spec", pendingIds("cuaderno"));
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "encargo enviado");
        production.aiCompleted(task, "spec guardado");
        assertThat(task(task).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(pendingIds("cuaderno")).isEmpty();
        assertThat(batch("cuaderno")).isEmpty();

        // Y una publicación con la hora ya pasada no entra
        publication("cuaderno", -1, "IN_PRODUCTION");
        assertThat(pendingIds("cuaderno")).isEmpty();
    }

    @Test
    @Order(8)
    void anOrderWhosePublicationsWereAllCancelledIsCancelledInsteadOfSent() throws Exception {
        publication("vacia", 45, "IN_PRODUCTION");
        assertThat(handler.obsolete(job("vacia", List.of(id(45))))).isFalse();
        write(post("/api/publications/" + id(45) + "/status-changes"), "{\"status\":\"CANCELLED\",\"reason\":\"OTHER\"}")
                .andExpect(status().isOk());
        assertThat(handler.obsolete(job("vacia", List.of(id(45))))).isTrue();
        // Una publicación cancelada no sale en lo que se manda a Claude
        assertThat(handler.inputs(job("vacia", List.of(id(45)))).get("publications")).isEqualTo(List.of());
        // Dado de alta saltándose la comprobación (como si se hubiera cancelado después), no sale: se cancela
        String task = production.submitAi(new com.socialhub.production.TaskRequest(AiRequestType.WEEKLY_SPEC.stationId(),
                "vacia", AiRequestType.WEEKLY_SPEC.taskType(), "Spec", "Spec", List.of(id(45)), List.of()), "SONNET");
        waitFor(() -> "CANCELLED".equals(task(task).get("status").asString()), "encargo obsoleto cancelado");
        assertThat(Files.exists(drive.resolve("encargos").resolve("para-claude").resolve(task + "-1.json"))).isFalse();
    }

    @Test
    @Order(9)
    void twoOrdersForTheSamePublicationAtTheSameTimeLeaveOnlyOne() throws Exception {
        publication("vacia", 50, "IN_PRODUCTION");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Boolean>> done = new java.util.ArrayList<>();
            var start = new java.util.concurrent.CountDownLatch(1);
            for (int i = 0; i < 2; i++) {
                done.add(pool.submit(() -> {
                    start.await();
                    try {
                        orders.submit("vacia", AiRequestType.WEEKLY_SPEC, "Spec", "Spec", List.of(id(50)));
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (var f : done) if (f.get()) created++;
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    private com.socialhub.ai.AiJob job(String promotion, List<String> inputs) {
        return new com.socialhub.ai.AiJob("t", AiRequestType.WEEKLY_SPEC, promotion, "el-delito", "Spec", "Spec", inputs, Instant.now());
    }

    // ---------------------------------------------------------------- utilidades

    private void command(String taskId, String type) throws Exception {
        write(post("/api/tasks/" + taskId + "/commands"), "{\"type\":\"" + type + "\"}").andExpect(status().isOk());
    }

    private void fail(String taskId, int attempt) throws Exception {
        Path dir = drive.resolve("encargos");
        waitFor(() -> Files.exists(dir.resolve("para-claude").resolve(taskId + "-" + attempt + ".json")), "encargo en Drive");
        Path file = Files.createDirectories(dir.resolve("de-claude")).resolve(taskId + "-" + attempt + ".json");
        Files.writeString(file, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"failed","outputs":{},
                 "error":"Sin acceso a la web","agent":{"runtime":"tarea programada en la nube"}}""".formatted(taskId));
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
}
