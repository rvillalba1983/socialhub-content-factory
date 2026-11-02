package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Encargos de piezas de extremo a extremo: botón (API), carpeta vigilada y motor, en modo real. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.engine.poll-millis=200",
        "socialhub.engine.initial-delay-millis=200",
        "socialhub.pieces.watch-millis=500",
        "socialhub.pieces.watch-initial-delay-millis=500"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PiecesApiTest {

    @TempDir static Path drive;
    static Path promo;
    static Path watched;

    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void folders() throws Exception {
        promo = Files.createDirectories(drive.resolve("DeLito"));
        watched = Files.createDirectories(promo.resolve("SocialHub/encargos"));
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1200, 1600);
        g.dispose();
        ImageIO.write(image, "png", promo.resolve("page 58.png").toFile());
        // Fuera de la carpeta vigilada: solo se encarga con el botón
        Files.createDirectories(promo.resolve("specs"));
        Files.writeString(promo.resolve("specs/semana2.json"), spec("4_martes_historia", "Semana 2", "../"));
    }

    /** @param up ruta desde la carpeta del spec hasta la de la promoción, por ejemplo «../» */
    static String spec(String name, String out, String up) {
        return """
                {"out": "%s%s", "leeme": "0_LEEME.txt",
                 "piezas": [{"tipo": "historia", "nombre": "%s", "dia": "Mar 3 nov 13:00",
                             "img": "%spage 58.png", "titulo": "Pregunta grande", "texto": "frase pequeña"}]}
                """.formatted(up, out, name, up);
    }

    @Test
    @Order(1)
    void aPromotionWithAFolderIsRegistered() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}")
                .andExpect(status().isCreated());
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE",
                 "resources":[{"kind":"DRIVE","location":%s,"description":"Carpeta de la campaña"}]}
                """.formatted(json.writeValueAsString(promo.toString())))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void theButtonOrdersTheSpecAndTheEngineGeneratesThePieces() throws Exception {
        mvc.perform(get("/api/promotions/poemario/piece-specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folder").value(watched.toString()))
                .andExpect(jsonPath("$.items.length()").value(0));

        String body = write(post("/api/promotions/poemario/piece-orders"),
                "{\"spec_path\":" + json.writeValueAsString(promo.resolve("specs/semana2.json").toString()) + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String taskId = json.readTree(body).get("task_id").asString();

        JsonNode task = waitForTask(taskId, "COMPLETED");
        assertThat(task.get("agent").get("kind").asString()).isEqualTo("LOCAL");
        assertThat(task.get("result").asString()).startsWith("1 piezas");
        assertThat(promo.resolve("Semana 2/4_martes_historia.jpg")).exists();
        assertThat(promo.resolve("Semana 2/0_LEEME.txt")).exists();
    }

    @Test
    @Order(3)
    void specsOutsideThePromotionFoldersAreRejected() throws Exception {
        Path outside = Files.createDirectories(drive.resolve("Otra"));
        Files.writeString(outside.resolve("spec.json"), spec("x", "Fuera", ""));
        write(post("/api/promotions/poemario/piece-orders"),
                "{\"spec_path\":" + json.writeValueAsString(outside.resolve("spec.json").toString()) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Fuera de las carpetas")));
        write(post("/api/promotions/no-existe/piece-orders"), "{\"spec_path\":\"/tmp/spec.json\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void aNewSpecInTheWatchedFolderBecomesATask() throws Exception {
        Files.writeString(watched.resolve("semana3.json"), spec("1_lunes_historia", "Semana 3", "../../"));
        Thread.sleep(1500);
        // Un spec ya encargado no se vuelve a encargar
        assertThat(json.readTree(mvc.perform(get("/api/promotions/poemario/piece-specs")).andReturn().getResponse()
                .getContentAsString()).get("items").size()).isEqualTo(1);
        String taskId = null;
        long deadline = System.currentTimeMillis() + 30_000;
        while (taskId == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            JsonNode specs = json.readTree(mvc.perform(get("/api/promotions/poemario/piece-specs"))
                    .andReturn().getResponse().getContentAsString());
            for (JsonNode item : specs.get("items")) {
                if (item.get("name").asString().equals("semana3.json") && item.has("task_id")) {
                    taskId = item.get("task_id").asString();
                }
            }
        }
        assertThat(taskId).as("tarea creada desde la carpeta vigilada").isNotNull();
        waitForTask(taskId, "COMPLETED");
        assertThat(promo.resolve("Semana 3/1_lunes_historia.jpg")).exists();
    }

    @Test
    @Order(5)
    void piecesAreAttachedToThePublicationTheSpecNames() throws Exception {
        write(post("/api/promotions/poemario/publications"), """
                {"platform":"INSTAGRAM","type":"Historia","title":"Pregunta grande","scheduled_on":"2030-11-03",
                 "scheduled_time":"13:00","initial_status":"IN_PRODUCTION"}""")
                .andExpect(jsonPath("$.id").value("2030-11-03-instagram"));
        String withPublication = spec("2_martes_historia", "Semana 4", "../")
                .replace("\"dia\"", "\"publicacion\": \"2030-11-03-instagram\", \"dia\"");
        Files.writeString(promo.resolve("specs/semana4.json"), withPublication);
        Files.writeString(promo.resolve("specs/mala.json"), withPublication.replace("2030-11-03-instagram", "no-existe"));

        write(post("/api/promotions/poemario/piece-orders"),
                "{\"spec_path\":" + json.writeValueAsString(promo.resolve("specs/mala.json").toString()) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no existe en esta promoción")));

        String body = write(post("/api/promotions/poemario/piece-orders"),
                "{\"spec_path\":" + json.writeValueAsString(promo.resolve("specs/semana4.json").toString()) + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        waitForTask(json.readTree(body).get("task_id").asString(), "COMPLETED");

        String generated = promo.resolve("Semana 4/2_martes_historia.jpg").toRealPath().toString();
        mvc.perform(get("/api/publications/2030-11-03-instagram/files"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].path").value(generated))
                .andExpect(jsonPath("$.items[0].origin").value("SPEC"))
                .andExpect(jsonPath("$.items[0].available").value(true));

        // A mano: solo archivos que existen dentro de las carpetas de la promoción, sin repetir
        String image = promo.resolve("page 58.png").toRealPath().toString();
        write(post("/api/publications/2030-11-03-instagram/files"), "{\"path\":" + json.writeValueAsString(image) + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("MANUAL"));
        write(post("/api/publications/2030-11-03-instagram/files"), "{\"path\":" + json.writeValueAsString(image) + "}")
                .andExpect(status().isBadRequest());
        write(post("/api/publications/2030-11-03-instagram/files"),
                "{\"path\":" + json.writeValueAsString(drive.resolve("Otra/spec.json").toString()) + "}")
                .andExpect(status().isBadRequest());
        write(post("/api/publications/2030-11-03-instagram/files"),
                "{\"path\":" + json.writeValueAsString(promo.resolve("SocialHub/../../Otra/spec.json").toString()) + "}")
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/publications/2030-11-03-instagram/files").param("path", image)
                        .header("X-SocialHub-Request", "1"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/publications/2030-11-03-instagram/files").param("path", image)
                        .header("X-SocialHub-Request", "1"))
                .andExpect(status().isNotFound());
        assertThat(Path.of(image)).exists();
        mvc.perform(get("/api/publications/2030-11-03-instagram/files")).andExpect(jsonPath("$.items.length()").value(1));
    }

    private JsonNode waitForTask(String id, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        JsonNode task;
        do {
            Thread.sleep(200);
            task = json.readTree(mvc.perform(get("/api/tasks/{id}", id)).andReturn().getResponse().getContentAsString());
        } while (!task.get("status").asString().equals(expected) && System.currentTimeMillis() < deadline);
        assertThat(task.get("status").asString()).as("tarea %s: %s", id, task.path("error").asString("")).isEqualTo(expected);
        return task;
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
