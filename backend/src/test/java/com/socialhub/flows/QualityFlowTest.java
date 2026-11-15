package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Avisos de calidad y su aceptación» (manual, §4), de extremo a extremo: los avisos del código (texto, identidad,
 * duplicados y archivos) y los de la revisión de Claude bloquean «Aprobada» y «Programada»; cada uno se corrige o se
 * acepta con un motivo; y el aviso desaparece o deja de bloquear al momento, sin esperar a ninguna pasada.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QualityFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path material;
    static Path square;
    static Path squarePng;
    static Path vertical;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        material = Files.createDirectories(drive.resolve("DeLito/material"));
        square = image("cuadrada.jpg", 1080, 1080, "jpg");
        squarePng = image("cuadrada.png", 1080, 1080, "png");
        vertical = image("vertical.jpg", 1080, 1920, "jpg");
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"MATERIAL\",\"location\":" + f.q(material) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/brands/el-delito/identity", """
                {"tone":"","audience":"","forbidden_topics":[],"preferred_expressions":[],"forbidden_expressions":["compra ya"],
                 "colors":[],"font":"","logo":"","visual_style":"","photo_style":"","call_to_action":"Cuéntanos qué te parece",
                 "fixed_hashtags":"#eldelito"}"""));
    }

    @Test
    @Order(2)
    void aPostThatKeepsEveryRuleHasNoNoticesAndGoesThroughReviewToApproved() throws Exception {
        Flow f = flow();
        post("2026-12-01", "Post", "Un poema nuevo. Cuéntanos qué te parece.", "#eldelito #poesia", square);
        JsonNode quality = f.read("/api/publications/2026-12-01-instagram/quality");
        assertThat(quality.get("open").asInt()).isZero();
        assertThat(quality.get("issues").size()).isZero();
        for (String next : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            f.post("/api/publications/2026-12-01-instagram/status-changes", "{\"status\":\"" + next + "\"}").andExpect(status().isOk());
        }
        assertThat(f.status("poemario", "2026-12-01-instagram")).isEqualTo("APPROVED");
    }

    @Test
    @Order(3)
    void everyKindOfCodeNoticeAppearsBlocksAndExplainsItself() throws Exception {
        Flow f = flow();
        String tags = String.join(" ", java.util.stream.IntStream.rangeClosed(1, 31).mapToObj(i -> "#etiqueta" + i).toList());
        // El texto cabe en lo que admite la API (2200), pero con los hashtags se pasa del máximo de Instagram
        post("2026-12-02", "Post", "Texto largo. ".repeat(165), tags, squarePng);
        List<String> keys = keys("2026-12-02-instagram");
        assertThat(keys).contains("TEXT_TOO_LONG", "HASHTAGS_TOO_MANY", "CTA_MISSING", "HASHTAG_MISSING:#eldelito",
                "FILE_FORMAT:" + squarePng);
        assertThat(message("2026-12-02-instagram", "FILE_FORMAT:" + squarePng)).contains("JPEG").contains("cuadrada.png");
        assertThat(message("2026-12-02-instagram", "HASHTAGS_TOO_MANY")).contains("31").contains("30");

        // Lo prohibido y una historia que no es vertical
        post("2026-12-03", "Historia", "", "", square);
        assertThat(keys("2026-12-03-instagram")).containsExactly("IMAGE_RATIO:" + square)
                .as("las historias no necesitan texto ni llamada a la acción");
        assertThat(message("2026-12-03-instagram", "IMAGE_RATIO:" + square)).contains("1080×1080").contains("9:16");
        post("2026-12-04", "Carrusel", "¡Compra YA! Cuéntanos qué te parece.", "#eldelito", square);
        assertThat(keys("2026-12-04-instagram")).containsExactly("FORBIDDEN:compra ya", "CAROUSEL_COUNT");

        // Bloquean: «En revisión» sí, «Aprobada» no
        f.post("/api/publications/2026-12-02-instagram/status-changes", "{\"status\":\"DRAFT\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-02-instagram/status-changes", "{\"status\":\"IN_REVIEW\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-02-instagram/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No pasa el control de calidad: 5 avisos sin resolver")));
        assertThat(f.status("poemario", "2026-12-02-instagram")).isEqualTo("IN_REVIEW");

        // Un aviso se corrige y desaparece en el acto (el texto vuelve a caber y a llevar lo que pide la marca)
        post("2026-12-02", "Post", "Corto. Cuéntanos qué te parece.", "#eldelito", squarePng);
        assertThat(keys("2026-12-02-instagram")).containsExactly("FILE_FORMAT:" + squarePng);
    }

    @Test
    @Order(4)
    void aDuplicateTextAppearsOnBothPublicationsUntilOneIsCancelled() throws Exception {
        Flow f = flow();
        post("2026-12-05", "Post", "Un poema nuevo. Cuéntanos qué te parece.", "#eldelito", square);
        assertThat(keys("2026-12-05-instagram")).containsExactly("DUPLICATE:2026-12-01-instagram");
        assertThat(keys("2026-12-01-instagram")).containsExactly("DUPLICATE:2026-12-05-instagram");
        f.ok(f.post("/api/publications/2026-12-05-instagram/status-changes", "{\"status\":\"CANCELLED\",\"reason\":\"REPEATED\"}"));
        assertThat(keys("2026-12-01-instagram")).isEmpty();
    }

    @Test
    @Order(5)
    void aNoticeIsAcceptedWithAReasonAndStopsBlockingButTheOthersStillBlock() throws Exception {
        Flow f = flow();
        String id = "2026-12-02-instagram";
        // Sin motivo (o demasiado corto), o de un aviso que no existe: no se acepta
        f.post("/api/publications/" + id + "/quality-acceptances", "{\"key\":" + f.q("FILE_FORMAT:" + squarePng) + ",\"reason\":\"ok\"}")
                .andExpect(status().isBadRequest());
        f.post("/api/publications/" + id + "/quality-acceptances", "{\"key\":\"NO_EXISTE\",\"reason\":\"Porque sí\"}")
                .andExpect(status().isBadRequest());
        f.post("/api/publications/no-existe/quality-acceptances", "{\"key\":\"X\",\"reason\":\"Porque sí\"}").andExpect(status().isNotFound());
        assertThat(f.read("/api/publications/" + id + "/quality").get("open").asInt()).isEqualTo(1);

        JsonNode accepted = f.ok(f.post("/api/publications/" + id + "/quality-acceptances",
                "{\"key\":" + f.q("FILE_FORMAT:" + squarePng) + ",\"reason\":\"  El PNG es de una captura, no importa  \"}"));
        assertThat(accepted.get("open").asInt()).isZero();
        JsonNode issue = accepted.get("issues").get(0);
        assertThat(issue.get("accepted").asBoolean()).isTrue();
        assertThat(issue.get("accepted_reason").asString()).isEqualTo("El PNG es de una captura, no importa");
        assertThat(issue.get("accepted_at").asString()).isNotBlank();

        // Con todo aceptado, pasa; una publicación con otro aviso abierto, no
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-04-instagram/status-changes", "{\"status\":\"DRAFT\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-04-instagram/status-changes", "{\"status\":\"IN_REVIEW\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-04-instagram/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict());
        // Aceptar uno solo de dos deja el otro bloqueando
        f.ok(f.post("/api/publications/2026-12-04-instagram/quality-acceptances", "{\"key\":\"CAROUSEL_COUNT\",\"reason\":\"Es un carrusel de una\"}"));
        f.post("/api/publications/2026-12-04-instagram/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 aviso sin resolver")));
    }

    @Test
    @Order(6)
    void anApprovedPostThatBreaksARuleCannotBeScheduledUntilItIsFixedOrAccepted() throws Exception {
        Flow f = flow();
        String id = "2026-12-01-instagram";
        post("2026-12-01", "Post", "Un poema nuevo. COMPRA YA. Cuéntanos qué te parece.", "#eldelito", square);
        assertThat(f.status("poemario", id)).isEqualTo("APPROVED");
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}").andExpect(status().isConflict());
        assertThat(f.status("poemario", id)).isEqualTo("APPROVED");
        // Nada se programa: no hay ningún trabajo de publicación
        f.get("/api/publications/" + id + "/publish-job").andExpect(status().isNotFound());

        post("2026-12-01", "Post", "Un poema nuevo. Cuéntanos qué te parece.", "#eldelito", square);
        assertThat(keys(id)).isEmpty();
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}").andExpect(status().isOk());
        assertThat(f.status("poemario", id)).isEqualTo("SCHEDULED");
        // Programada no se edita: ni el texto ni los archivos, así que no se le puede colar un aviso
        f.put("/api/publications/" + id, publication("2026-12-01", "Post", "Otro texto", "#eldelito")).andExpect(status().isConflict());
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void claudesReviewAddsNoticesThatBlockAndANewReviewReplacesThem() throws Exception {
        Flow f = flow();
        String id = "2026-12-01-instagram";
        String task = f.order("poemario", "QUALITY_REVIEW", "Revisa la publicación", id);
        // Claude recibe lo que revisa: el texto y los archivos de la publicación
        JsonNode request = f.request(task);
        assertThat(request.get("inputs").get("publication").get("caption").asString()).isEqualTo("Un poema nuevo. Cuéntanos qué te parece.");
        assertThat(request.get("inputs").get("files").size()).isEqualTo(1);

        f.complete(task, "{\"issues\":[{\"message\":\"«nuevo» suena a anuncio\"},{\"message\":\"Falta una tilde en «poema»\"}],\"summary\":\"Casi\"}");
        assertThat(f.task(task).get("result").asString()).isEqualTo("2 avisos de Claude · Casi");
        List<String> claude = keys(id).stream().filter(k -> k.startsWith("CLAUDE:")).toList();
        assertThat(claude).hasSize(2);
        assertThat(f.read("/api/publications/" + id + "/quality").get("open").asInt()).isEqualTo(2);
        // Bloquean igual que los del código
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}").andExpect(status().isConflict());

        // Se acepta uno con motivo, y el otro lo resuelve una revisión nueva que ya no lo menciona
        f.ok(f.post("/api/publications/" + id + "/quality-acceptances", "{\"key\":\"" + claude.get(0) + "\",\"reason\":\"Es a propósito\"}"));
        assertThat(f.read("/api/publications/" + id + "/quality").get("open").asInt()).isEqualTo(1);
        String again = f.order("poemario", "QUALITY_REVIEW", "Revisa otra vez", id);
        f.complete(again, "{\"issues\":[],\"summary\":\"Ahora sí\"}");
        assertThat(keys(id)).isEmpty();
        assertThat(f.read("/api/publications/" + id + "/quality").get("open").asInt()).isZero();
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}").andExpect(status().isOk());
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
    }


    // ------------------------------------------------------------------ utilidades

    private static String publication(String day, String type, String caption, String hashtags) {
        return """
                {"platform":"INSTAGRAM","type":"%s","title":"Pieza del %s","scheduled_on":"%s","scheduled_time":"19:00",
                 "caption":%s,"hashtags":%s}""".formatted(type, day, day, Flow.quote(caption), Flow.quote(hashtags));
    }

    /** Crea (o actualiza) la publicación de ese día con ese texto y ese único archivo, ya en producción. */
    private void post(String day, String type, String caption, String hashtags, Path file) throws Exception {
        Flow f = flow();
        String id = day + "-instagram";
        boolean exists = f.get("/api/promotions/poemario/publications?limit=500").andReturn().getResponse().getContentAsString()
                .contains("\"" + id + "\"");
        if (!exists) {
            f.ok(f.post("/api/promotions/poemario/publications", """
                    {"platform":"INSTAGRAM","type":"%s","title":"Pieza del %s","scheduled_on":"%s","scheduled_time":"19:00",
                     "initial_status":"IN_PRODUCTION"}""".formatted(type, day, day)));
        }
        f.ok(f.put("/api/publications/" + id, publication(day, type, caption, hashtags)));
        for (JsonNode attached : f.read("/api/publications/" + id + "/files").get("items")) {
            if (!attached.get("path").asString().equals(file.toString())) {
                f.delete("/api/publications/" + id + "/files?path=" + java.net.URLEncoder.encode(attached.get("path").asString(),
                        java.nio.charset.StandardCharsets.UTF_8)).andExpect(status().isNoContent());
            }
        }
        boolean attached = false;
        for (JsonNode item : f.read("/api/publications/" + id + "/files").get("items")) {
            if (item.get("path").asString().equals(file.toString())) attached = true;
        }
        if (!attached) f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(file) + "}").andExpect(status().isCreated());
    }

    private List<String> keys(String publicationId) throws Exception {
        List<String> keys = new ArrayList<>();
        flow().read("/api/publications/" + publicationId + "/quality").get("issues").forEach(i -> keys.add(i.get("key").asString()));
        return keys;
    }

    private String message(String publicationId, String key) throws Exception {
        for (JsonNode i : flow().read("/api/publications/" + publicationId + "/quality").get("issues")) {
            if (i.get("key").asString().equals(key)) return i.get("message").asString();
        }
        throw new AssertionError("No hay el aviso " + key);
    }

    private static Path image(String name, int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path file = material.resolve(name);
        ImageIO.write(image, format, file.toFile());
        return file.toRealPath();
    }
}
