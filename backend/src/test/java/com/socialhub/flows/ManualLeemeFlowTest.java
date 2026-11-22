package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Ruta LEEME manual» (manual, §6), de extremo a extremo y con el motor en marcha por su cuenta: ninguna vía
 * llega porque faltan menos de 7 h (sin API oficial ni Publora, y Publer ya no da tiempo) → LEEME en «aprobados» con
 * los archivos por orden, el texto y los hashtags → aviso → Raúl publica a mano → 2 h después de la hora Claude lo
 * comprueba: con enlace queda «Publicada» y el aviso desaparece; sin encontrarla, «Error de publicación».
 */
@FlowTest
@TestPropertySource(properties = {
        "socialhub.publishing.initial-delay-millis=200",
        "socialhub.publishing.tick-millis=300"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManualLeemeFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static Path slide1;
    static Path slide2;
    static Path story;

    @Autowired MockMvc mvc;
    /** Estas pruebas se escribieron con el margen de 6 horas de antes del ajuste: lo fijan para conservar sus horarios. */
    @Autowired com.socialhub.settings.PublishSettings margin;
    @org.junit.jupiter.api.BeforeEach void sixHourMargin() { margin.update(360); }
    @org.junit.jupiter.api.AfterEach void defaultMargin() { margin.update(com.socialhub.settings.PublishSettings.DEFAULT_MINUTES); }
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
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        slide1 = image("diapositiva-1.jpg", 1080, 1350, new Color(178, 34, 34));
        slide2 = image("diapositiva-2.jpg", 1080, 1350, new Color(30, 90, 160));
        story = image("historia.jpg", 1080, 1920, new Color(40, 120, 70));

        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(approved) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/brands/el-delito/publer-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"account_name\":\"El DeLito\"}]}"));
    }

    @Test
    @Order(2)
    void withoutMarginTheCarouselGoesManualWithItsReadmeAndOnceFoundPublishedTheNoticeDisappears() throws Exception {
        Flow f = flow();
        // Faltan 6 h 30 min: pasa el margen de 6 h para programarla, pero Publer (7 h) ya no da tiempo y no hay otra vía
        String id = create("2026-11-10", "16:30", "Carrusel", "Tres versos", "Un verso para leer despacio.", "#poesia #versos");
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(slide1) + "}"));
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(slide2) + "}"));
        approve(id);
        Instant at = LocalDateTime.of(2026, 11, 10, 16, 30).atZone(FlowClock.MADRID).toInstant();

        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        f.waitFor(() -> "MANUAL".equals(f.job(id).get("status").asString()), "LEEME manual");
        JsonNode job = f.job(id);
        assertThat(job.get("log").toString()).contains("Publer: ya no da tiempo").contains("Publora: no configurada")
                .contains("API oficial: no configurada");

        // El LEEME: en «aprobados», con los archivos por orden, el texto, los hashtags y lo que falló
        Path leeme = Path.of(job.get("manual_file").asString());
        assertThat(Files.isSameFile(leeme.getParent(), approved)).isTrue();
        String text = Files.readString(leeme);
        assertThat(text).contains("PUBLICAR A MANO · Tres versos").contains("Red: INSTAGRAM · Carrusel")
                .contains("Cuándo: 10/11/2026 16:30 (hora de España)").contains(id)
                .contains("Un verso para leer despacio.").contains("#poesia #versos")
                .contains("Publer: ya no da tiempo").contains("lo comprobará 2 h después");
        assertThat(text.indexOf(slide1.toRealPath().toString())).isPositive()
                .isLessThan(text.indexOf(slide2.toRealPath().toString()));
        // Aviso a la vista: la incidencia de la publicación y la alerta de «Qué hacer ahora»
        assertThat(f.publication("poemario", id).get("flag").asString()).startsWith("Publicación manual con LEEME");
        assertThat(f.alerts()).anyMatch(a -> a.get("target_id").asString().equals(id) && a.get("kind").asString().equals("BLOCKED"));

        // A la hora pasa a «Publicando» y SocialHub espera: a mano se comprueba 2 h después
        clock.set(at.plus(Duration.ofMinutes(30)));
        f.waitFor(() -> "PUBLISHING".equals(f.status("poemario", id)), "publicando");
        assertThat(f.job(id).get("status").asString()).isEqualTo("MANUAL");
        // «Publicada» no se pone a mano: solo con la confirmación de la plataforma
        var byHand = f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"PUBLISHED\"}").andReturn().getResponse();
        assertThat(byHand.getStatus()).as(byHand.getContentAsString()).isBetween(400, 499);
        assertThat(f.status("poemario", id)).isEqualTo("PUBLISHING");

        // 2 h después Claude comprueba el perfil: la encuentra con su enlace
        clock.set(at.plus(Duration.ofHours(2)).plusSeconds(60));
        f.waitFor(() -> "CHECKING".equals(f.job(id).get("status").asString()) && f.job(id).hasNonNull("task_id"), "comprobación pedida");
        String check = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(check).get("status").asString()), "comprobación enviada");
        JsonNode request = f.pcRequest(check);
        assertThat(request.get("type").asString()).isEqualTo("socialhub.publish-check");
        f.complete(check, "{\"published\":true,\"url\":\"https://www.instagram.com/p/ABC123/\"}");
        f.waitFor(() -> "PUBLISHED".equals(f.status("poemario", id)), "publicada");
        assertThat(f.job(id).get("url").asString()).isEqualTo("https://www.instagram.com/p/ABC123/");
        assertThat(f.publication("poemario", id).get("confirmation").asString()).contains("instagram.com/p/ABC123");
        // Resuelta: el aviso desaparece
        assertThat(f.hasAlert(id)).isFalse();
        // Y la incidencia «Publicación manual con LEEME» se quita de la publicación ya publicada
        assertThat(f.publication("poemario", id).has("flag") && !f.publication("poemario", id).get("flag").isNull()).isFalse();
        clock.reset();
    }

    @Test
    @Order(3)
    void ifNothingIsFoundPublishedTwoHoursLaterItIsAPublishErrorAndSaysWhy() throws Exception {
        Flow f = flow();
        clock.reset();
        String id = create("2026-11-10", "16:45", "Historia", "Pregunta grande", null, null);
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(story) + "}"));
        approve(id);
        Instant at = LocalDateTime.of(2026, 11, 10, 16, 45).atZone(FlowClock.MADRID).toInstant();

        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        f.waitFor(() -> "MANUAL".equals(f.job(id).get("status").asString()), "LEEME manual");
        assertThat(Files.readString(Path.of(f.job(id).get("manual_file").asString())))
                .contains("Red: INSTAGRAM · Historia").contains("(sin texto)").contains(story.toRealPath().toString());

        clock.set(at.plus(Duration.ofHours(2)).plusSeconds(60));
        f.waitFor(() -> "CHECKING".equals(f.job(id).get("status").asString()) && f.job(id).hasNonNull("task_id"), "comprobación pedida");
        String check = f.job(id).get("task_id").asString();
        f.waitFor(() -> "WAITING".equals(f.task(check).get("status").asString()), "comprobación enviada");
        f.complete(check, "{\"published\":false,\"note\":\"No está en el perfil\"}");
        f.waitFor(() -> "PUBLISH_ERROR".equals(f.status("poemario", id)), "no aparece");
        assertThat(f.job(id).get("last_error").asString()).isEqualTo("No está en el perfil");
        assertThat(f.publication("poemario", id).get("flag").asString()).startsWith("Error de publicación: No está en el perfil");
        assertThat(f.alerts()).anyMatch(a -> a.get("target_id").asString().equals(id) && a.get("kind").asString().equals("FAILED"));
        clock.reset();
    }

    // ---------------------------------------------------------------- utilidades

    private String create(String day, String time, String type, String title, String caption, String hashtags) throws Exception {
        Flow f = flow();
        String extra = caption == null ? "" : ",\"caption\":" + f.q(caption) + ",\"hashtags\":" + f.q(hashtags);
        JsonNode created = f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"%s",
                 "initial_status":"IN_PRODUCTION"%s}""".formatted(type, title, day, time, extra)));
        return created.get("id").asString();
    }

    private void approve(String id) throws Exception {
        Flow f = flow();
        for (String s : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            var response = f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"" + s + "\"}").andReturn().getResponse();
            assertThat(response.getStatus()).as(id + " → " + s + ": " + response.getContentAsString()).isEqualTo(200);
        }
    }

    private static Path image(String name, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path file = approved.resolve(name);
        ImageIO.write(image, "jpg", file.toFile());
        return file;
    }
}
