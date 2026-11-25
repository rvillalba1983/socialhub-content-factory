package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Flujo «Piezas con Java2D y FFmpeg» (manual, §7), de extremo a extremo: Claude devuelve el spec de un lote, SocialHub
 * genera las piezas (carrusel, historia y reel, dibujados con Java2D y montados con FFmpeg), las asocia a cada
 * publicación y, lo que faltaba por comprobar, esas piezas pasan solas el control de calidad de su red y su tipo, así que
 * la publicación llega a «Aprobada» con solo ponerle el texto.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PiecesToQualityFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path material;
    static Path generated;
    static Path page;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;
    @Autowired com.socialhub.publishing.PublishingCommands publishing;

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
        generated = Files.createDirectories(drive.resolve("DeLito/generados"));
        page = material.resolve("pagina 58.png");
        BufferedImage image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(180, 60, 40), 1600, 1200, new Color(250, 230, 200)));
        g.fillRect(0, 0, 1600, 1200);
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
        create("2026-12-01", "INSTAGRAM", "Carrusel", "Tres versos");
        create("2026-12-02", "INSTAGRAM", "Historia", "Pregunta grande");
        create("2026-12-03", "INSTAGRAM", "Reel", "Leer en voz alta");
        create("2026-12-04", "TIKTOK", "Reel", "Lo mismo en TikTok");
        create("2026-12-05", "TIKTOK", "Carrusel", "Carrusel vertical");
    }

    @Test
    @Order(2)
    void theSpecBecomesPiecesAttachedToEachPublicationDrawnForRealAndTheOrderIsComplete() throws Exception {
        Flow f = flow();
        String task = f.order("poemario", "WEEKLY_SPEC", "spec del lote", "2026-12-01-instagram", "2026-12-02-instagram",
                "2026-12-03-instagram", "2026-12-04-tiktok", "2026-12-05-tiktok");
        assertThat(f.request(task).get("inputs").get("publications").size()).isEqualTo(5);
        assertThat(f.request(task).get("inputs").get("format_note").asString()).contains("9:16");

        String spec = """
                {"out":%s,"piezas":[
                 {"tipo":"carrusel","nombre":"1_carrusel","publicacion":"2026-12-01-instagram",
                  "slides":[{"img":%s,"texto":"Un verso largo que tiene que partirse en varias líneas para caber","titulo":"Con púa"},
                            {"img":%s,"texto":"Otro verso"}],
                  "cierre":{"img":%s,"lineas":["El DeLito","30 poemas que se leen","Enlace en la bio"]}},
                 {"tipo":"historia","nombre":"2_historia","publicacion":"2026-12-02-instagram","img":%s,"titulo":"Pregunta grande",
                  "texto":"frase pequeña","cta":"Enlace en la bio"},
                 {"tipo":"reel","nombre":"3_reel","publicacion":"2026-12-03-instagram","img":%s,"gancho":"Frase de tres segundos",
                  "versos":["verso uno"],"final":["El DeLito","un poema de Lito"]},
                 {"tipo":"reel","nombre":"4_reel_tiktok","publicacion":"2026-12-04-tiktok","img":%s,"gancho":"Frase de tres segundos",
                  "versos":["verso uno"],"final":["El DeLito"]},
                 {"tipo":"carrusel","nombre":"5_carrusel_tiktok","publicacion":"2026-12-05-tiktok",
                  "slides":[{"img":%s,"texto":"Primer verso","pagina":58},{"img":%s,"texto":"Segundo verso","pagina":59}],
                  "cierre":{"img":%s,"lineas":["El DeLito"],"pagina":60},
                  "sonido":{"modo":"network","tema":"Púa (Litoques)"}}]}"""
                .formatted(f.q(generated), f.q(page), f.q(page), f.q(page), f.q(page), f.q(page), f.q(page), f.q(page), f.q(page), f.q(page));
        f.complete(task, "{\"spec\":" + spec + ",\"summary\":\"4 piezas\"}");
        String resultText = f.task(task).get("result").asString();
        assertThat(resultText).contains("piezas encargadas");
        String pieces = resultText.replaceAll(".*\\(tarea ([0-9a-f-]+)\\).*", "$1");
        f.waitFor(() -> "COMPLETED".equals(f.task(pieces).get("status").asString()), "piezas generadas", 240);
        assertThat(f.task(pieces).get("result").asString()).startsWith("5 piezas");

        // Cada pieza, con sus medidas y dibujada de verdad (no un lienzo en blanco)
        for (int i = 1; i <= 3; i++) assertImage(generated.resolve("1_carrusel_" + i + ".jpg"), 1080, 1350);
        assertImage(generated.resolve("2_historia.jpg"), 1080, 1920);
        assertThat(generated.resolve("3_reel.mp4")).exists();
        assertThat(Files.size(generated.resolve("3_reel.mp4"))).isPositive().isLessThanOrEqualTo(9_500_000L);
        assertImage(generated.resolve("3_reel_portada.jpg"), 1080, 1920);
        assertThat(generated.resolve("4_reel_tiktok.mp4")).exists();

        // Y asociadas a la publicación que dice el spec, con el origen SPEC
        assertThat(files("2026-12-01-instagram")).hasSize(3);
        assertThat(files("2026-12-02-instagram")).hasSize(1);
        assertThat(files("2026-12-03-instagram")).hasSize(2);
        assertThat(files("2026-12-04-tiktok")).hasSize(2);
        for (JsonNode item : f.read("/api/publications/2026-12-01-instagram/files").get("items")) {
            assertThat(item.get("origin").asString()).isEqualTo("SPEC");
        }

        // Hallazgo B: el carrusel de TikTok sale en 9:16, el de Instagram sigue en 4:5
        for (int i = 1; i <= 3; i++) assertImage(generated.resolve("5_carrusel_tiktok_" + i + ".jpg"), 1080, 1920);
        assertThat(files("2026-12-05-tiktok")).hasSize(3);

        // Hallazgo K: cada diapositiva con su página del PDF y el nombre del archivo, en la API de la publicación
        JsonNode tiktokFiles = f.read("/api/publications/2026-12-05-tiktok/files").get("items");
        List<String> pages = new ArrayList<>();
        for (JsonNode item : tiktokFiles) {
            assertThat(item.get("source_name").asString()).isEqualTo("pagina 58.png");
            pages.add(item.get("path").asString().replaceAll(".*_(\\d)\\.jpg", "$1") + "=" + item.get("page").asInt());
        }
        assertThat(pages).containsExactlyInAnyOrder("1=58", "2=59", "3=60");
        // Una pieza sin página dicha no inventa ninguna
        for (JsonNode item : f.read("/api/publications/2026-12-01-instagram/files").get("items")) {
            assertThat(item.path("page").isNumber()).isFalse();
            assertThat(item.get("source_name").asString()).isEqualTo("pagina 58.png");
        }

        // Decisión 23: el sonido que pide el spec queda en la publicación; las demás, sin sonido
        JsonNode sound = f.read("/api/promotions/poemario/publications?limit=500").get("items");
        for (JsonNode publication : sound) {
            boolean tiktokCarousel = publication.get("id").asString().equals("2026-12-05-tiktok");
            assertThat(publication.get("sound").get("mode").asString()).isEqualTo(tiktokCarousel ? "network" : "none");
            if (tiktokCarousel) {
                assertThat(publication.get("sound").get("track").asString()).isEqualTo("Púa (Litoques)");
                assertThat(publication.get("sound").get("label").asString()).isEqualTo("Elegir en la red: Púa (Litoques)");
            }
        }
    }

    @Test
    @Order(3)
    void theGeneratedPiecesPassTheQualityControlOfEachNetworkAndTypeSoOnlyTheTextIsMissing() throws Exception {
        Flow f = flow();
        for (String id : List.of("2026-12-01-instagram", "2026-12-02-instagram", "2026-12-03-instagram", "2026-12-04-tiktok")) {
            List<String> keys = keys(id);
            assertThat(keys).as("avisos de archivos de " + id).noneMatch(k -> k.startsWith("FILE") || k.startsWith("IMAGE")
                    || k.startsWith("CAROUSEL") || k.startsWith("VIDEO"));
            // Lo único que falta es el texto (las historias ni eso)
            assertThat(keys).isEqualTo(id.contains("12-02") ? List.of() : List.of("TEXT_MISSING"));
        }

        // Con el texto puesto, cada una llega a «Aprobada» sin aceptar nada
        for (String id : List.of("2026-12-01-instagram", "2026-12-03-instagram", "2026-12-04-tiktok")) {
            String platform = id.contains("tiktok") ? "TIKTOK" : "INSTAGRAM";
            String type = id.contains("12-01") ? "Carrusel" : "Reel";
            f.ok(f.put("/api/publications/" + id, """
                    {"platform":"%s","type":"%s","title":"x","scheduled_on":"%s","scheduled_time":"19:00",
                     "caption":"Texto propio de la pieza %s.","hashtags":"#poesia"}""".formatted(platform, type, id.substring(0, 10), id)));
        }
        for (String id : List.of("2026-12-01-instagram", "2026-12-02-instagram", "2026-12-03-instagram", "2026-12-04-tiktok")) {
            for (String next : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
                var response = f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"" + next + "\"}").andReturn().getResponse();
                assertThat(response.getStatus()).as(id + " → " + next + ": " + response.getContentAsString() + " " + keys(id)).isEqualTo(200);
            }
            assertThat(f.status("poemario", id)).isEqualTo("APPROVED");
        }
    }

    @Test
    @Order(4)
    void theSoundOfAPublicationIsChangedAndValidatedThroughTheApi() throws Exception {
        Flow f = flow();
        var set = f.put("/api/publications/2026-12-05-tiktok/sound", "{\"mode\":\"litoques_track\",\"track\":\"Púa\"}");
        JsonNode changed = f.ok(set);
        assertThat(changed.get("sound").get("mode").asString()).isEqualTo("litoques_track");
        assertThat(changed.get("sound").get("label").asString()).isEqualTo("Tema de Litoques en el vídeo: Púa");
        // Sin sonido no lleva tema aunque venga uno
        JsonNode none = f.ok(f.put("/api/publications/2026-12-05-tiktok/sound", "{\"mode\":\"none\",\"track\":\"x\"}"));
        assertThat(none.get("sound").get("mode").asString()).isEqualTo("none");
        assertThat(none.get("sound").path("track").isString()).isFalse();
        // Con tema de Litoques o de la red hace falta el nombre
        var missing = f.put("/api/publications/2026-12-05-tiktok/sound", "{\"mode\":\"network\"}").andReturn().getResponse();
        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(missing.getContentAsString()).contains("Falta el nombre del tema");
        assertThat(f.put("/api/publications/2026-12-05-tiktok/sound", "{\"mode\":\"loud\"}").andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(f.put("/api/publications/no-existe/sound", "{\"mode\":\"none\"}").andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @Order(5)
    void regeneratingKeepsTheChosenSoundAndRefreshesThePageAndSourceOfAttachedPieces() throws Exception {
        Flow f = flow();
        String id = "2026-12-05-tiktok";
        f.ok(f.put("/api/publications/" + id + "/sound", "{\"mode\":\"litoques_track\",\"track\":\"Elegido por Raúl\"}"));
        List<Path> same = files(id).stream().map(Path::of).toList();
        // Se regenera el mismo archivo con otra página y otro origen, y el spec pide otro sonido
        var regenerated = same.stream().map(path -> new com.socialhub.publishing.PublishingCommands.GeneratedFile(path, 99, "nueva imagen.png")).toList();
        int added = publishing.attachGeneratedPieces("poemario", id, regenerated,
                new com.socialhub.publishing.PublishingCommands.SoundChoice("network", "Púa (Litoques)"));
        assertThat(added).isZero();
        for (JsonNode item : f.read("/api/publications/" + id + "/files").get("items")) {
            assertThat(item.get("page").asInt()).isEqualTo(99);
            assertThat(item.get("source_name").asString()).isEqualTo("nueva imagen.png");
            assertThat(item.get("origin").asString()).isEqualTo("SPEC");
        }
        JsonNode kept = f.read("/api/promotions/poemario/publications?limit=500").get("items");
        for (JsonNode publication : kept) {
            if (publication.get("id").asString().equals(id)) {
                assertThat(publication.get("sound").get("mode").asString()).isEqualTo("litoques_track");
                assertThat(publication.get("sound").get("track").asString()).isEqualTo("Elegido por Raúl");
            }
        }
        // Sin sonido elegido, sí se aplica el del spec
        f.ok(f.put("/api/publications/" + id + "/sound", "{\"mode\":\"none\"}"));
        publishing.attachGeneratedPieces("poemario", id, regenerated,
                new com.socialhub.publishing.PublishingCommands.SoundChoice("network", "Púa (Litoques)"));
        for (JsonNode publication : f.read("/api/promotions/poemario/publications?limit=500").get("items")) {
            if (publication.get("id").asString().equals(id)) {
                assertThat(publication.get("sound").get("mode").asString()).isEqualTo("network");
            }
        }
    }

    @Test
    @Order(6)
    void theSoundOfAScheduledPublicationCannotBeChangedThroughTheApi() throws Exception {
        Flow f = flow();
        String id = "2026-12-02-instagram";
        f.ok(f.put("/api/brands/el-delito/publer-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"account_name\":\"El DeLito\"}]}"));
        f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
        assertThat(f.status("poemario", id)).isEqualTo("SCHEDULED");
        var response = f.put("/api/publications/" + id + "/sound", "{\"mode\":\"network\",\"track\":\"Púa\"}").andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("programada");
    }

    // ------------------------------------------------------------------ utilidades

    private void create(String day, String platform, String type, String title) throws Exception {
        flow().ok(flow().post("/api/promotions/poemario/publications", """
                {"platform":"%s","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"19:00","initial_status":"IN_PRODUCTION"}"""
                .formatted(platform, type, title, day)));
    }

    private List<String> files(String id) throws Exception {
        List<String> paths = new ArrayList<>();
        flow().read("/api/publications/" + id + "/files").get("items").forEach(i -> paths.add(i.get("path").asString()));
        return paths;
    }

    private List<String> keys(String id) throws Exception {
        List<String> keys = new ArrayList<>();
        flow().read("/api/publications/" + id + "/quality").get("issues").forEach(i -> keys.add(i.get("key").asString()));
        return keys;
    }

    /** Tamaño exacto y algo dibujado: la imagen tiene varios colores distintos, no un lienzo liso. */
    private static void assertImage(Path file, int width, int height) throws Exception {
        BufferedImage image = ImageIO.read(file.toFile());
        assertThat(image).as(file.toString()).isNotNull();
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
        Set<Integer> colours = new HashSet<>();
        for (int x = 0; x < width; x += 37) {
            for (int y = 0; y < height; y += 37) colours.add(image.getRGB(x, y) >> 3);
        }
        assertThat(colours.size()).as("colores distintos en " + file.getFileName()).isGreaterThan(8);
    }
}
