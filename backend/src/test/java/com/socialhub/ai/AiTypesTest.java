package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cada tipo de encargo de extremo a extremo: qué datos lleva el encargo y qué hace SocialHub con el resultado. */
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
class AiTypesTest {

    @TempDir static Path drive;
    static Path orders;
    static Path reports;
    static Path material;

    @Autowired MockMvc mvc;
    @Autowired com.socialhub.publishing.PublishingQueries publishing;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void folders() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        reports = Files.createDirectories(drive.resolve("DeLito/informes"));
        material = Files.createDirectories(drive.resolve("DeLito/material"));
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1200, 1600);
        g.dispose();
        ImageIO.write(image, "png", material.resolve("pagina 58.png").toFile());
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE"}""")
                .andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), """
                {"items":[{"role":"ORDERS","location":%s},{"role":"REPORTS","location":%s},{"role":"MATERIAL","location":%s}]}"""
                .formatted(q(orders), q(reports), q(material))).andExpect(status().isOk());
        // Esta prueba envía muchos encargos: sin límite diario que estorbe
        write(put("/api/ai/settings"), "{\"daily_limit\":100,\"models\":[]}").andExpect(status().isOk());
        write(put("/api/brands/el-delito/identity"), """
                {"tone":"Cercano","audience":"","forbidden_topics":[],"preferred_expressions":[],"forbidden_expressions":[],
                 "colors":[],"font":"","logo":"","visual_style":"","photo_style":"","call_to_action":"","fixed_hashtags":""}""")
                .andExpect(status().isOk());
        write(post("/api/promotions/poemario/publications"), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Pregunta grande","scheduled_on":"2030-11-12",
                 "scheduled_time":"13:00","initial_status":"IN_PRODUCTION"}""")
                .andExpect(jsonPath("$.id").value("2030-11-12-instagram"));
    }

    @Test
    @Order(2)
    void captionsAreSavedInThePublication() throws Exception {
        write(post("/api/promotions/poemario/ai-orders"), "{\"type\":\"CAPTIONS\",\"goal\":\"Textos\",\"inputs\":[\"no-existe\"]}")
                .andExpect(status().isBadRequest());
        String task = order("CAPTIONS", "Textos para la historia", "2030-11-12-instagram");
        JsonNode request = request(task);
        assertThat(request.get("inputs").get("publications").get(0).get("title").asString()).isEqualTo("Pregunta grande");
        assertThat(request.get("inputs").get("brand_identity").get("tone").asString()).isEqualTo("Cercano");
        assertThat(request.get("inputs").get("brand_identity").has("audience")).isFalse();

        result(task, completed(task, """
                {"publications":[{"id":"2030-11-12-instagram","caption":"¿Y si el delito fuera leer?","hashtags":"#poesia #eldelito"}],
                 "summary":"Tono cercano"}"""));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "textos guardados");
        assertThat(task(task).get("result").asString()).isEqualTo("1 texto guardado para revisar · Tono cercano");
        JsonNode publication = publication("2030-11-12-instagram");
        assertThat(publication.get("caption").asString()).isEqualTo("¿Y si el delito fuera leer?");
        assertThat(publication.get("hashtags").asString()).isEqualTo("#poesia #eldelito");

        // Un texto para una publicación que no se pidió invalida el resultado entero
        String other = order("CAPTIONS", "Otra vez", "2030-11-12-instagram");
        result(other, completed(other, """
                {"publications":[{"id":"2030-11-12-instagram","caption":"Nuevo"},{"id":"otra","caption":"x"}]}"""));
        waitFor(() -> "FAILED".equals(task(other).get("status").asString()), "resultado que no sirve");
        assertThat(task(other).get("error").asString()).contains("El resultado no sirve").contains("«otra»");
        assertThat(publication("2030-11-12-instagram").get("caption").asString()).isEqualTo("¿Y si el delito fuera leer?");
    }

    @Test
    @Order(3)
    void researchReportsAreSavedInTheReportsFolder() throws Exception {
        String task = order("RESEARCH", "Salas de poesía en Granada", "Granada");
        result(task, """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed",
                 "outputs":{"report":"# Salas\\n\\n- La Tertulia","summary":"1 sala"},
                 "sources":[{"title":"La Tertulia","url":"https://example.org/tertulia"}],
                 "agent":{"runtime":"tarea programada en la nube"}}""".formatted(task));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "informe guardado");
        List<Path> files = list(reports);
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName().toString()).endsWith("-investigacion-investigacion.md");
        assertThat(Files.readString(files.getFirst())).contains("# Salas").contains("## Fuentes")
                .contains("[La Tertulia](https://example.org/tertulia)");
        assertThat(task(task).get("result").asString()).startsWith("1 sala · Informe: ");
    }

    @Test
    @Order(4)
    void theWeeklySpecOrdersThePiecesAndAttachesThemToThePublication() throws Exception {
        String task = order("WEEKLY_SPEC", "spec de la semana", "2030-11-12");
        JsonNode request = request(task);
        assertThat(request.get("inputs").get("week").get("from").asString()).isEqualTo("2030-11-11");
        assertThat(request.get("inputs").get("publications").get(0).get("id").asString()).isEqualTo("2030-11-12-instagram");
        assertThat(request.get("inputs").get("folders").get("MATERIAL").asString()).isEqualTo(material.toString());

        String spec = """
                {"out":%s,"piezas":[{"tipo":"historia","nombre":"2_martes_historia","publicacion":"2030-11-12-instagram",
                 "img":%s,"titulo":"Pregunta grande","texto":"frase"}]}"""
                .formatted(q(drive.resolve("DeLito/generados")), q(material.resolve("pagina 58.png")));
        // «generados» aún no es una carpeta de la promoción: el spec no se puede encargar
        result(task, completed(task, "{\"spec\":" + spec + "}"));
        waitFor(() -> "FAILED".equals(task(task).get("status").asString()), "spec rechazado");
        assertThat(task(task).get("error").asString()).contains("El spec no se puede encargar").contains("Fuera de las carpetas");
        assertThat(list(orders.resolve("specs-de-claude"))).extracting(p -> p.getFileName().toString())
                .containsExactly("spec-2030-11-11.rechazado");

        write(put("/api/promotions/poemario/folders"), "{\"items\":[{\"role\":\"GENERATED\",\"location\":"
                + q(Files.createDirectories(drive.resolve("DeLito/generados"))) + "}]}").andExpect(status().isOk());
        mvc.perform(post("/api/tasks/{id}/commands", task).header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"RETRY\"}")).andExpect(status().isOk());
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "segundo intento enviado");
        result(task, completed(task, "{\"spec\":" + spec + ",\"summary\":\"1 historia\"}"));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "spec aplicado");
        String resultText = task(task).get("result").asString();
        assertThat(resultText).contains("spec-2030-11-11.json").contains("piezas encargadas");
        String pieces = resultText.replaceAll(".*\\(tarea ([0-9a-f-]+)\\).*", "$1");
        waitFor(() -> "COMPLETED".equals(task(pieces).get("status").asString()), "piezas generadas");
        assertThat(drive.resolve("DeLito/generados/2_martes_historia.jpg")).exists();
        mvc.perform(get("/api/publications/2030-11-12-instagram/files"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].origin").value("SPEC"));
    }

    @Test
    @Order(5)
    void theQualityReviewAddsClaudesIssuesToThePublication() throws Exception {
        mvc.perform(get("/api/ai/settings")).andExpect(jsonPath("$.models[?(@.type == 'QUALITY_REVIEW')].model").value("HAIKU"));
        String task = order("QUALITY_REVIEW", "Revisa la publicación", "2030-11-12-instagram");
        JsonNode request = request(task);
        assertThat(request.get("model").asString()).isEqualTo("haiku");
        assertThat(request.get("type").asString()).isEqualTo("socialhub.quality-review");
        assertThat(request.get("inputs").get("publication").get("caption").asString()).isEqualTo("¿Y si el delito fuera leer?");
        assertThat(request.get("inputs").get("files").size()).isEqualTo(1);

        result(task, completed(task, "{\"issues\":[{\"message\":\"«leer» no concuerda con el tono\"}],\"summary\":\"Casi\"}"));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "revisión aplicada");
        assertThat(task(task).get("result").asString()).isEqualTo("1 aviso de Claude · Casi");
        JsonNode report = json.readTree(mvc.perform(get("/api/publications/2030-11-12-instagram/quality"))
                .andReturn().getResponse().getContentAsString());
        JsonNode claude = null;
        for (JsonNode issue : report.get("issues")) if (issue.get("source").asString().equals("CLAUDE")) claude = issue;
        assertThat(claude).isNotNull();
        assertThat(claude.get("message").asString()).isEqualTo("«leer» no concuerda con el tono");
        assertThat(claude.get("key").asString()).startsWith("CLAUDE:");

        // Una revisión nueva sustituye a la anterior
        String again = order("QUALITY_REVIEW", "Revisa otra vez", "2030-11-12-instagram");
        result(again, completed(again, "{\"issues\":[]}"));
        waitFor(() -> "COMPLETED".equals(task(again).get("status").asString()), "segunda revisión");
        assertThat(task(again).get("result").asString()).isEqualTo("Revisión sin avisos");
        assertThat(mvc.perform(get("/api/publications/2030-11-12-instagram/quality")).andReturn().getResponse()
                .getContentAsString()).doesNotContain("CLAUDE:");
    }

    @Test
    @Order(6)
    void theEditorialMemoryGoesInEveryOrder() throws Exception {
        // Raúl corrige el texto que propuso Claude: queda el antes y el después
        write(put("/api/publications/2030-11-12-instagram"), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Pregunta grande","scheduled_on":"2030-11-12",
                 "scheduled_time":"13:00","caption":"¿Y si leer fuera el delito?","hashtags":"#poesia #eldelito"}""")
                .andExpect(status().isOk());
        // Y descarta el texto con su motivo
        write(post("/api/publications/2030-11-12-instagram/text-rejections"), "{\"reason\":\"TONE\",\"note\":\"Demasiado serio\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").doesNotExist());
        write(post("/api/publications/2030-11-12-instagram/text-rejections"), "{\"reason\":\"TONE\"}")
                .andExpect(status().isConflict());
        // Cancelar exige el motivo
        write(post("/api/promotions/poemario/publications"), """
                {"platform":"TIKTOK","type":"Reel","title":"Reel sobrante","scheduled_on":"2030-11-13","scheduled_time":"19:00"}""")
                .andExpect(status().isCreated());
        write(post("/api/publications/2030-11-13-tiktok/status-changes"), "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isBadRequest());
        write(post("/api/publications/2030-11-13-tiktok/status-changes"), "{\"status\":\"CANCELLED\",\"reason\":\"REPEATED\"}")
                .andExpect(status().isOk());
        write(post("/api/brands/el-delito/decisions"), "{\"text\":\"Nada de precios\"}").andExpect(status().isCreated());

        mvc.perform(get("/api/brands/el-delito/editorial-memory"))
                .andExpect(jsonPath("$.decisions[0].text").value("Nada de precios"))
                .andExpect(jsonPath("$.rejections.length()").value(2))
                .andExpect(jsonPath("$.rejections[0].kind").value("PUBLICATION"))
                .andExpect(jsonPath("$.rejections[0].reason").value("REPEATED"))
                .andExpect(jsonPath("$.rejections[1].kind").value("TEXT"))
                .andExpect(jsonPath("$.rejections[1].note").value("Demasiado serio"))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].before_caption").value("¿Y si el delito fuera leer?"))
                .andExpect(jsonPath("$.corrections[0].after_caption").value("¿Y si leer fuera el delito?"));

        String task = order("RESEARCH", "Ideas nuevas", "Granada");
        JsonNode memory = request(task).get("inputs").get("editorial_memory");
        assertThat(memory.get("decisions").get(0).asString()).isEqualTo("Nada de precios");
        assertThat(memory.get("rejections").get(1).get("reason").asString()).isEqualTo("Tono");
        assertThat(memory.get("corrections").get(0).get("raul_caption").asString()).isEqualTo("¿Y si leer fuera el delito?");
        assertThat(memory.get("published_last_8_weeks").size()).isZero();
    }

    @Test
    @Order(7)
    void weeklyProposalsBecomePublicationsToReviewAndTheSpecOnlyUsesApprovedOnes() throws Exception {
        String task = order("WEEKLY_PROPOSALS", "Propón la semana", "2030-11-18");
        JsonNode request = request(task);
        assertThat(request.get("inputs").get("week").get("from").asString()).isEqualTo("2030-11-18");
        assertThat(request.get("inputs").get("promotion").get("name").asString()).isEqualTo("Poemario");

        String twoProposals = """
                {"proposals":[
                  {"scheduled_on":"2030-11-18","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Carrusel",
                   "title":"Tres versos de la herida","idea":"Carrusel con tres versos","why":"Funciona el formato"},
                  {"scheduled_on":"2030-11-20","scheduled_time":"13:00","platform":"tiktok","type":"Reel","title":"Leer en voz alta"}],
                 "summary":"Semana tranquila"}""";
        result(task, completed(task, twoProposals.replace("2030-11-20", "2030-11-30")));
        waitFor(() -> "FAILED".equals(task(task).get("status").asString()), "propuesta fuera de la semana");
        assertThat(task(task).get("error").asString()).contains("Propuesta 2").contains("fuera de la semana");

        mvc.perform(post("/api/tasks/{id}/commands", task).header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"RETRY\"}")).andExpect(status().isOk());
        waitFor(() -> "WAITING".equals(task(task).get("status").asString()), "segundo intento");
        result(task, completed(task, twoProposals));
        waitFor(() -> "COMPLETED".equals(task(task).get("status").asString()), "propuestas creadas");
        assertThat(task(task).get("result").asString()).isEqualTo("2 propuestas para revisar en Publicaciones · Semana tranquila");
        JsonNode first = publication("2030-11-18-instagram");
        assertThat(first.get("status").asString()).isEqualTo("PROPOSAL");
        assertThat(first.get("note").asString()).isEqualTo("Idea: Carrusel con tres versos · Por qué: Funciona el formato");
        assertThat(publication("2030-11-20-tiktok").get("status").asString()).isEqualTo("PROPOSAL");
        // «Qué hacer ahora» cuenta las propuestas por promoción con una consulta agrupada
        assertThat(publishing.proposalCounts()).containsEntry("poemario", 2L);

        // Se aprueba una y la otra no: el spec solo ve la aprobada
        write(post("/api/publications/2030-11-18-instagram/status-changes"), "{\"status\":\"IN_PRODUCTION\"}").andExpect(status().isOk());
        String spec = order("WEEKLY_SPEC", "spec", "2030-11-18");
        JsonNode planned = request(spec).get("inputs").get("publications");
        assertThat(planned.size()).isEqualTo(1);
        assertThat(planned.get(0).get("id").asString()).isEqualTo("2030-11-18-instagram");
    }

    // ---------------------------------------------------------------- utilidades

    private String order(String type, String goal, String input) throws Exception {
        String body = write(post("/api/promotions/poemario/ai-orders"), """
                {"type":"%s","goal":"%s","inputs":["%s"]}""".formatted(type, goal, input))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(id).get("status").asString()), "encargo enviado");
        return id;
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
                 "agent":{"runtime":"tarea programada en la nube","model":"sonnet"}}""".formatted(taskId, outputs);
    }

    private void result(String taskId, String content) throws Exception {
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(taskId + "-" + System.nanoTime() + ".json");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(30)));
    }

    private JsonNode task(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/{id}", id)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode publication(String id) throws Exception {
        JsonNode page = json.readTree(mvc.perform(get("/api/promotions/poemario/publications")).andReturn().getResponse()
                .getContentAsString());
        for (JsonNode p : page.get("items")) if (p.get("id").asString().equals(id)) return p;
        throw new AssertionError("No existe " + id);
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
}
