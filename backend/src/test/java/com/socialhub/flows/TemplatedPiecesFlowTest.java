package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.domain.KitCodec;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Fase 13, bloque 3, de extremo a extremo: la marca tiene un kit aprobado, el encargo del spec lleva sus plantillas (sin el
 * SVG), un spec con una plantilla que no existe se rechaza con el motivo en español y el bueno genera la cita de El DeLito, el
 * cierre del carrusel de TikTok en 9:16 y la historia con sus plantillas encima de la ilustración.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplatedPiecesFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path material;
    static Path generated;
    static Path page;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;
    @Autowired BrandKits kits;
    @Autowired KitCodec codec;

    static String specTask;
    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    private static final List<String> LOT = List.of("2026-12-01-instagram", "2026-12-02-tiktok", "2026-12-03-instagram");

    @Test
    @Order(1)
    void setupWithAnApprovedKit() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        material = Files.createDirectories(drive.resolve("DeLito/material"));
        generated = Files.createDirectories(drive.resolve("DeLito/generados"));
        page = material.resolve("page 12.png");
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(180, 60, 40), 1200, 1600, new Color(250, 230, 200)));
        g.fillRect(0, 0, 1200, 1600);
        g.dispose();
        ImageIO.write(image, "png", page.toFile());
        page = page.toRealPath();

        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"MATERIAL\",\"location\":" + f.q(material) + "},{\"role\":\"GENERATED\",\"location\":" + f.q(generated) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        create("2026-12-01", "INSTAGRAM", "Cita");
        create("2026-12-02", "TIKTOK", "Carrusel");
        create("2026-12-03", "INSTAGRAM", "Historia");
        var kit = kits.propose("el-delito", codec.write(KitFixtures.delito()), null);
        kits.approve(kit.id());
    }

    @Test
    @Order(2)
    void theSpecOrderCarriesTheApprovedTemplatesWithoutTheirSvgAndTheRuleToUseThem() throws Exception {
        Flow f = flow();
        String task = f.order("poemario", "WEEKLY_SPEC", "spec del lote", LOT.toArray(String[]::new));
        specTask = task;
        JsonNode inputs = f.request(task).get("inputs");
        JsonNode templates = inputs.get("brand_templates");
        assertThat(templates.size()).isEqualTo(5);
        Set<String> ids = new HashSet<>();
        for (JsonNode t : templates) {
            ids.add(t.get("id").asString());
            assertThat(t.has("svg")).as("sin el SVG entero").isFalse();
            assertThat(t.get("ratio").asString()).isIn("4:5", "9:16");
        }
        assertThat(ids).containsExactlyInAnyOrder("cita-4x5", "cita-9x16", "cierre-4x5", "cierre-9x16", "historia-9x16");
        JsonNode quote = null;
        for (JsonNode t : templates) if (t.get("id").asString().equals("cita-4x5")) quote = t;
        assertThat(quote.get("piece").asString()).isEqualTo("cita");
        assertThat(quote.get("required_fields").get(0).asString()).isEqualTo("cita");
        assertThat(quote.get("optional_fields").get(0).asString()).isEqualTo("autor");
        assertThat(inputs.get("brand_templates_rule").asString()).contains("plantilla").contains("campos").contains("ENCIMA");
        assertThat(inputs.get("format_note").asString()).contains("TikTok").contains("9:16").contains("sonido").contains("pagina");
        // Lo añadido es pequeño: muy por debajo del tope de 512 KB
        assertThat(templates.toString().length() + inputs.get("brand_templates_rule").asString().length()).isLessThan(20_000);

        // Una plantilla que no existe se rechaza al recibir el spec, con el motivo en español
        String bad = spec("plantilla-que-no-existe");
        f.result(task, Flow.completed(task, "{\"spec\":" + bad + "}"));
        f.waitFor(() -> "FAILED".equals(f.task(task).get("status").asString()), "spec rechazado");
        assertThat(f.task(task).get("error").asString()).contains("El spec no se puede encargar")
                .contains("la plantilla «plantilla-que-no-existe» no existe en el kit aprobado de la marca")
                .contains("Plantillas de «cierre»: cierre-4x5, cierre-9x16");
        assertThat(Flow.list(orders.resolve("specs-de-claude"))).extracting(p -> p.getFileName().toString()).allMatch(n -> n.endsWith(".rechazado"));
        assertThat(generated).isEmptyDirectory();
    }

    @Test
    @Order(3)
    void theGoodSpecDrawsTheQuoteTheTikTokClosingAndTheStoryWithTheirTemplates() throws Exception {
        Flow f = flow();
        // El mismo encargo, otra vez (el lote sigue ocupado por el que falló): reintento y ahora el spec bueno
        String task = specTask;
        f.ok(f.post("/api/tasks/" + task + "/commands", "{\"type\":\"RETRY\"}"));
        f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "segundo intento enviado");
        f.complete(task, "{\"spec\":" + spec("cierre-9x16") + ",\"summary\":\"3 piezas\"}");
        String pieces = f.task(task).get("result").asString().replaceAll(".*\\(tarea ([0-9a-f-]+)\\).*", "$1");
        f.waitFor(() -> "COMPLETED".equals(f.task(pieces).get("status").asString()), "piezas generadas", 240);
        assertThat(f.task(pieces).get("result").asString()).startsWith("3 piezas");

        assertImage(generated.resolve("1_cita.jpg"), 1080, 1350);
        for (int i = 1; i <= 2; i++) assertImage(generated.resolve("2_carrusel_tiktok_" + i + ".jpg"), 1080, 1920);
        assertImage(generated.resolve("3_historia.jpg"), 1080, 1920);
        assertThat(f.request(task).get("inputs").get("publications").size()).isEqualTo(3);

        JsonNode quoteFiles = f.read("/api/publications/2026-12-01-instagram/files").get("items");
        assertThat(quoteFiles).hasSize(1);
        assertThat(quoteFiles.get(0).get("page").asInt()).isEqualTo(12);
        assertThat(quoteFiles.get(0).get("source_name").asString()).isEqualTo("page 12.png");
        assertThat(f.publication("poemario", "2026-12-01-instagram").get("sound").get("mode").asString()).isEqualTo("litoques_track");
        assertThat(f.publication("poemario", "2026-12-01-instagram").get("sound").get("track").asString()).isEqualTo("Púa");
    }

    /** El spec de las tres publicaciones; {@code closing} es la plantilla del cierre del carrusel de TikTok. */
    private static String spec(String closing) {
        Flow f = new Flow(null, null, orders);
        return """
                {"out":%s,"piezas":[
                 {"tipo":"cita","nombre":"1_cita","publicacion":"2026-12-01-instagram","plantilla":"cita-4x5","pagina":12,"img":%s,
                  "campos":{"cita":"Tres versos del libro,\\nLiterales,\\nCon su autor","autor":"LITO"},
                  "sonido":{"modo":"litoques_track","tema":"Púa"}},
                 {"tipo":"carrusel","nombre":"2_carrusel_tiktok","publicacion":"2026-12-02-tiktok",
                  "slides":[{"img":%s,"texto":"Un verso","pagina":12}],
                  "cierre":{"img":%s,"lineas":[],"plantilla":%s,"campos":{"titulo":"El DeLito","texto":"Enlace en la bio"},"pagina":13}},
                 {"tipo":"historia","nombre":"3_historia","publicacion":"2026-12-03-instagram","plantilla":"historia-9x16","img":%s,
                  "campos":{"titulo":"Pregunta","texto":"Frase pequeña","cta":"Enlace en la bio"}}]}"""
                .formatted(f.q(generated), f.q(page), f.q(page), f.q(page), f.q(closing), f.q(page));
    }

    private void create(String day, String platform, String type) throws Exception {
        flow().ok(flow().post("/api/promotions/poemario/publications", """
                {"platform":"%s","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"19:00","initial_status":"IN_PRODUCTION"}"""
                .formatted(platform, type, type + " " + day, day)));
    }

    private static void assertImage(Path file, int width, int height) throws Exception {
        BufferedImage image = ImageIO.read(file.toFile());
        assertThat(image).as(file.toString()).isNotNull();
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
        Set<Integer> colours = new HashSet<>();
        for (int x = 0; x < width; x += 37) for (int y = 0; y < height; y += 37) colours.add(image.getRGB(x, y) >> 3);
        assertThat(colours.size()).as("colores distintos en " + file.getFileName()).isGreaterThan(2);
    }
}
