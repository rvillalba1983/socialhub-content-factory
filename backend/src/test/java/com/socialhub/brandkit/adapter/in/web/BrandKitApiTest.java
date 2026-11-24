package com.socialhub.brandkit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitPiece;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Base64;
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
 * El kit de marca de extremo a extremo, con la base real: pedirlo, el encargo que sale a Drive, el resultado de Claude
 * (válido e inválido), la vista previa, aprobar y «Pedir otra versión».
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
        "socialhub.ai.auto-initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrandKitApiTest {

    @TempDir static Path drive;
    static Path orders;

    @Autowired MockMvc mvc;
    @Autowired KitCodec codec;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void folders() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        write(post("/api/brands"), "{\"name\":\"Marca Kit\",\"handle\":\"@marcakit\",\"color\":\"#D23A2E\"}").andExpect(status().isCreated());
        write(post("/api/brands"), "{\"name\":\"Sin Promo\",\"handle\":\"@sinpromo\",\"color\":\"#336699\"}").andExpect(status().isCreated());
        write(post("/api/promotions"), """
                {"brand_id":"marca-kit","name":"Lanzamiento","starts_on":"2026-11-03","ends_on":"2031-01-25","status":"ACTIVE"}""")
                .andExpect(status().isCreated());
        write(put("/api/brands/marca-kit/folders"), "{\"items\":[{\"role\":\"ORDERS\",\"location\":%s}]}"
                .formatted(json.writeValueAsString(orders.toString()))).andExpect(status().isOk());
        write(put("/api/ai/settings"), "{\"daily_limit\":100,\"models\":[]}").andExpect(status().isOk());
        write(put("/api/brands/marca-kit/identity"), """
                {"tone":"Cercano","audience":"","forbidden_topics":[],"preferred_expressions":[],"forbidden_expressions":[],
                 "colors":[],"font":"","logo":"","visual_style":"","photo_style":"","call_to_action":"","fixed_hashtags":""}""")
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void aBrandWithoutPromotionsCannotAskForAKitAndAnUnknownBrandIs404() throws Exception {
        write(post("/api/brands/sin-promo/kit/request"), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("aún no tiene ninguna promoción")));
        write(post("/api/brands/no-existe/kit/request"), "{}").andExpect(status().isNotFound());
        mvc.perform(get("/api/brands/no-existe/kit")).andExpect(status().isNotFound());
        mvc.perform(get("/api/brands/sin-promo/kit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").doesNotExist()).andExpect(jsonPath("$.pending").doesNotExist());
        // Sin kit pendiente no hay nada que aprobar ni rechazar: 409 con el mensaje de recargar
        write(post("/api/brands/marca-kit/kit/approve"), "{\"kit_id\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ese kit ya no es el pendiente; recarga."));
        write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":1,\"comment\":\"Más oscuro\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ese kit ya no es el pendiente; recarga."));
        write(post("/api/brands/marca-kit/kit/approve"), "{}").andExpect(status().isBadRequest());
        // La marca informa de las piezas que lleva su kit (valor por defecto de las marcas nuevas)
        for (JsonNode brand : fetch("/api/brands").get("items")) {
            if ("marca-kit".equals(brand.get("id").asString())) {
                assertThat(brand.get("kit_pieces")).extracting(JsonNode::asString).containsExactly("cierre", "historia");
            }
        }
    }

    @Test
    @Order(3)
    void theOrderHangsFromAPromotionAndCarriesTheRulesOfEachPiece() throws Exception {
        write(post("/api/brands/marca-kit/kit/request"), "{\"promotion_id\":\"otra\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no es de esta marca")));
        // Marca nueva: lleva cierre e historia; la cita se rechaza
        write(post("/api/brands/marca-kit/kit/request"), "{\"pieces\":[\"cita\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no lleva cita")));
        setPieces("marca-kit", "cita,cierre,historia");
        String task = requestKit("{\"pieces\":[\"cita\",\"cierre\",\"historia\"]}");
        JsonNode request = request(task);
        assertThat(request.get("type").asString()).isEqualTo("socialhub.brand-kit");
        assertThat(request.get("promotion").asString()).isEqualTo("lanzamiento");
        assertThat(request.get("brand").asString()).isEqualTo("marca-kit");
        assertThat(request.get("origin").asString()).isEqualTo("manual");
        assertThat(request.get("urgent").asBoolean()).isTrue();
        JsonNode inputs = request.get("inputs");
        assertThat(inputs.get("brand_name").asString()).isEqualTo("Marca Kit");
        assertThat(inputs.get("brand_identity").get("tone").asString()).isEqualTo("Cercano");
        assertThat(inputs.get("pieces")).extracting(p -> p.get("piece").asString()).containsExactly("cita", "cierre", "historia");
        assertThat(inputs.get("image_rule").asString()).contains("ENCIMA");
        assertThat(inputs.has("previous_kit")).isFalse();
        assertThat(request.get("deadline")).isNull();
        // Se pide a Claude con el modelo de diseño por defecto
        mvc.perform(get("/api/ai/settings")).andExpect(jsonPath("$.models[?(@.type == 'BRAND_KIT')].model").value("OPUS"))
                .andExpect(jsonPath("$.models[?(@.type == 'BRAND_KIT')].label").value("Kit de marca"));
    }

    @Test
    @Order(4)
    void aSecondKitCannotBeAskedWhileOneOrderIsOpenNeitherByTheKitNorByTheGenericOrder() throws Exception {
        write(post("/api/brands/marca-kit/kit/request"), "{\"pieces\":[\"cierre\"]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("encargo de kit abierto")));
        // «Nuevo encargo» de la pantalla de Claude: mismo criterio
        write(post("/api/promotions/lanzamiento/ai-orders"), "{\"type\":\"BRAND_KIT\",\"goal\":\"Diseña el kit\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("encargo de kit abierto")));
    }

    @Test
    @Order(5)
    void aValidResultIsPendingWithPreviewsAndNothingIsApprovedYet() throws Exception {
        String task = lastTask;
        result(task, completed(task, kitOutputs()));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "kit guardado");
        assertThat(task(task).get("result").asString()).contains("versión 1").contains("pendiente de tu aprobación").contains("5 plantillas");
        JsonNode view = fetch("/api/brands/marca-kit/kit");
        assertThat(gone(view, "approved")).isTrue();
        JsonNode pending = view.get("pending");
        assertThat(pending.get("status").asString()).isEqualTo("PENDING");
        assertThat(pending.get("version").asInt()).isEqualTo(1);
        assertThat(pending.get("source_task_id").asString()).isEqualTo(task);
        assertThat(pending.get("palette")).hasSize(4);
        JsonNode templates = pending.get("templates");
        assertThat(templates).hasSize(5);
        for (JsonNode t : templates) {
            assertThat(t.get("errors")).isEmpty();
            byte[] png = Base64.getDecoder().decode(t.get("preview_png_base64").asString());
            assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            assertThat(t.get("svg").asString()).startsWith("<svg");
        }
        assertThat(templates.get(0).get("piece").asString()).isEqualTo("cita");
        assertThat(templates.get(2).get("image").get("height").asDouble()).isEqualTo(700.0);
        assertThat(gone(templates.get(0), "image")).isTrue();
    }

    @Test
    @Order(6)
    void aPendingKitBlocksNewOrdersAndApprovingNeedsTheKitRaulIsLookingAt() throws Exception {
        // Con un kit pendiente no se puede pedir otro (ni por la ficha ni por el encargo genérico)
        write(post("/api/brands/marca-kit/kit/request"), "{\"pieces\":[\"cierre\"]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("kit pendiente")));
        write(post("/api/promotions/lanzamiento/ai-orders"), "{\"type\":\"BRAND_KIT\",\"goal\":\"Diseña el kit\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("kit pendiente")));
        // Aprobar otro id (por ejemplo, una versión que ya no es la pendiente) no aprueba el pendiente
        long pendingId = fetch("/api/brands/marca-kit/kit").get("pending").get("id").asLong();
        write(post("/api/brands/marca-kit/kit/approve"), "{\"kit_id\":" + (pendingId + 1000) + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ese kit ya no es el pendiente; recarga."));
        write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + (pendingId + 1000) + ",\"comment\":\"No\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ese kit ya no es el pendiente; recarga."));
        assertThat(fetch("/api/brands/marca-kit/kit").get("pending").get("id").asLong()).isEqualTo(pendingId);

        write(post("/api/brands/marca-kit/kit/approve"), "{\"kit_id\":" + pendingId + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.approved_at").exists());
        JsonNode view = fetch("/api/brands/marca-kit/kit");
        assertThat(view.get("approved").get("version").asInt()).isEqualTo(1);
        assertThat(gone(view, "pending")).isTrue();
        // Aprobar otra vez la misma versión (la pantalla estaba desfasada) es un conflicto
        write(post("/api/brands/marca-kit/kit/approve"), "{\"kit_id\":" + pendingId + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Ese kit ya no es el pendiente; recarga.")));
    }

    @Test
    @Order(7)
    void askingForAnotherVersionRejectsThePendingOneWithTheCommentAndOrdersANewOne() throws Exception {
        String task = requestKit("{\"pieces\":[\"cierre\",\"historia\"]}");
        result(task, completed(task, kitOutputsOf(KitFixtures.with(KitFixtures.delitoTemplates().stream()
                .filter(t -> t.piece() != KitPiece.CITA).toList()))));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "segundo kit");
        JsonNode pending = fetch("/api/brands/marca-kit/kit").get("pending");
        assertThat(pending.get("version").asInt()).isEqualTo(2);
        assertThat(pending.get("templates")).hasSize(3);
        long id = pending.get("id").asLong();

        write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + id + ",\"comment\":\"  \"}").andExpect(status().isBadRequest());
        write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + (id - 1) + ",\"comment\":\"Más oscuro\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ese kit ya no es el pendiente; recarga."));
        String body = write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + id + ",\"comment\":\"Más oscuro y con menos marco\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.kit.status").value("REJECTED"))
                .andExpect(jsonPath("$.kit.comment").value("Más oscuro y con menos marco")).andExpect(jsonPath("$.task_id").exists())
                .andReturn().getResponse().getContentAsString();
        String next = json.readTree(body).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(next).get("status").asString()), "encargo de otra versión enviado");
        lastTask = next;

        JsonNode request = request(next);
        assertThat(request.get("promotion").asString()).isEqualTo("lanzamiento");
        assertThat(request.get("goal").asString()).contains("Más oscuro y con menos marco");
        JsonNode inputs = request.get("inputs");
        assertThat(inputs.get("comment").asString()).isEqualTo("Más oscuro y con menos marco");
        assertThat(inputs.get("pieces")).extracting(p -> p.get("piece").asString()).containsExactly("cierre", "historia");
        // El kit anterior va resumido: paleta, ids y piezas, y el SVG solo de las piezas pedidas (no el de la cita)
        JsonNode previous = inputs.get("previous_kit");
        assertThat(previous.get("palette")).hasSize(4);
        assertThat(previous.get("templates")).hasSize(3);
        for (JsonNode t : previous.get("templates")) assertThat(t.get("svg").asString()).startsWith("<svg");
        assertThat(inputs.has("approved_kit")).isFalse();
        // El kit aprobado sigue en vigor y no hay pendiente hasta que Claude conteste
        JsonNode view = fetch("/api/brands/marca-kit/kit");
        assertThat(view.get("approved").get("version").asInt()).isEqualTo(1);
        assertThat(gone(view, "pending")).isTrue();
    }

    @Test
    @Order(8)
    void rejectingWithoutAskingForAnotherVersionOrdersNothing() throws Exception {
        String task = lastTask;
        result(task, completed(task, kitOutputsOf(KitFixtures.with(KitFixtures.delitoTemplates().stream()
                .filter(t -> t.piece() == KitPiece.CIERRE || t.piece() == KitPiece.HISTORIA).toList()))));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "kit con cierre e historia");
        long id = fetch("/api/brands/marca-kit/kit").get("pending").get("id").asLong();
        write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + id + ",\"comment\":\"No me gusta\",\"request_another\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.kit.status").value("REJECTED")).andExpect(jsonPath("$.task_id").doesNotExist());
        assertThat(gone(fetch("/api/brands/marca-kit/kit"), "pending")).isTrue();
    }

    @Test
    @Order(9)
    void withSeveralPromotionsRaulChoosesAndAnOpenOrderInAnyOfThemBlocksTheOthers() throws Exception {
        write(post("/api/promotions"), """
                {"brand_id":"marca-kit","name":"Segunda","starts_on":"2026-12-01","ends_on":"2027-01-25","status":"ACTIVE"}""")
                .andExpect(status().isCreated());
        write(post("/api/brands/marca-kit/kit/request"), "{\"pieces\":[\"cierre\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tiene 2")));
        String task = requestKit("{\"promotion_id\":\"segunda\",\"pieces\":[\"cierre\"]}");
        assertThat(request(task).get("promotion").asString()).isEqualTo("segunda");
        // El encargo abierto cuelga de «segunda», pero la marca es una: tampoco se puede pedir otro desde «lanzamiento»
        write(post("/api/brands/marca-kit/kit/request"), "{\"promotion_id\":\"lanzamiento\",\"pieces\":[\"cierre\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("«segunda»")));
        write(post("/api/promotions/lanzamiento/ai-orders"), "{\"type\":\"BRAND_KIT\",\"goal\":\"Diseña el kit\"}")
                .andExpect(status().isConflict());
        result(task, completed(task, kitOutputsOf(KitFixtures.with(KitFixtures.delitoTemplates().stream()
                .filter(t -> t.piece() == KitPiece.CIERRE).toList()))));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "kit de la segunda promoción");
        long id = fetch("/api/brands/marca-kit/kit").get("pending").get("id").asLong();
        String body = write(post("/api/brands/marca-kit/kit/reject"), "{\"kit_id\":" + id + ",\"comment\":\"Otra paleta\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String next = json.readTree(body).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(next).get("status").asString()), "otra versión enviada");
        assertThat(request(next).get("promotion").asString()).isEqualTo("segunda");
        lastTask = next;
    }

    @Test
    @Order(10)
    void anInvalidKitFailsTheOrderWithTheReasonsAndKeepsTheKitInForce() throws Exception {
        String task = lastTask;
        // Cierre sin la zona de la imagen y con los campos de una cita: no sirve
        String broken = codec.write(KitFixtures.with(List.of(KitFixtures.template("cierre-4x5", KitPiece.CIERRE,
                KitFixtures.cita(1080, 1350), null))));
        result(task, completed(task, "{\"kit\":" + broken + "}"));
        waitFor(() -> "FAILED".equals(task(task).get("status").asString()), "kit que no sirve");
        assertThat(task(task).get("error").asString()).contains("El resultado no sirve").contains("El kit no es válido");
        assertThat(gone(fetch("/api/brands/marca-kit/kit"), "pending")).isTrue();
        assertThat(fetch("/api/brands/marca-kit/kit").get("approved").get("version").asInt()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- utilidades

    private void setPieces(String brand, String pieces) {
        jdbc.update("update brand set kit_pieces = string_to_array(?, ',') where id = ?", pieces, brand);
    }

    private static String lastTask;

    private String requestKit(String body) throws Exception {
        String response = write(post("/api/brands/marca-kit/kit/request"), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(id).get("status").asString()), "encargo enviado");
        lastTask = id;
        return id;
    }

    /** La propiedad no viene o viene a null. */
    private static boolean gone(JsonNode node, String name) { return node.path(name).isMissingNode() || node.path(name).isNull(); }

    private String kitOutputs() { return kitOutputsOf(KitFixtures.delito()); }

    private String kitOutputsOf(com.socialhub.brandkit.domain.KitContent kit) {
        return "{\"kit\":" + codec.write(kit) + ",\"summary\":\"Marco dorado sobre tinta\"}";
    }

    private JsonNode fetch(String url) throws Exception {
        return json.readTree(mvc.perform(get(url))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode request(String taskId) throws Exception {
        try (Stream<Path> s = Files.list(orders.resolve("para-claude"))) {
            Path file = s.filter(p -> p.getFileName().toString().startsWith(taskId)).sorted().reduce((a, b) -> b).orElseThrow();
            return json.readTree(Files.readString(file));
        }
    }

    private static String completed(String taskId, String outputs) {
        return """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed","outputs":%s,
                 "agent":{"runtime":"tarea programada en la nube","model":"opus"}}""".formatted(taskId, outputs);
    }

    private void result(String taskId, String content) throws Exception {
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(taskId + "-" + System.nanoTime() + ".json");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private JsonNode task(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/{id}", id)).andReturn().getResponse().getContentAsString());
    }

    private void waitFor(Callable<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.call()) {
            assertThat(System.currentTimeMillis()).as(what).isLessThan(deadline);
            Thread.sleep(150);
        }
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request.header("X-SocialHub-Request", "1");
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
