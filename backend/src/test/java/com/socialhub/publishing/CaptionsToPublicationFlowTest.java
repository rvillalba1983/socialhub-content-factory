package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.flows.Flow;
import com.socialhub.flows.FlowClock;
import com.socialhub.flows.FlowTest;
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
 * Flujo «Textos de Claude → publicación» (manual, §4 y §11), de extremo a extremo: SocialHub pide solo los textos de lo
 * que está en producción sin texto (una vez, por lotes de 10 y sin historias); el resultado de Claude se guarda en cada
 * publicación; Raúl lo corrige (queda el antes y el después) o lo descarta con motivo; y al pasar a «Pendiente de
 * revisión» SocialHub pide solo la revisión de calidad, que bloquea hasta que se corrige o se acepta.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaptionsToPublicationFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path material;
    static Path photo;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;
    @Autowired AutoRequests autoRequests;

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
        BufferedImage image = new BufferedImage(1080, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1080, 1080);
        g.dispose();
        photo = material.resolve("foto.jpg");
        ImageIO.write(image, "jpg", photo.toFile());
        photo = photo.toRealPath();
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"MATERIAL\",\"location\":" + f.q(material) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/brands/el-delito/identity", """
                {"tone":"Cercano","audience":"","forbidden_topics":[],"preferred_expressions":[],"forbidden_expressions":[],
                 "colors":[],"font":"","logo":"","visual_style":"","photo_style":"","call_to_action":"Cuéntanos qué te parece",
                 "fixed_hashtags":"#eldelito"}"""));
    }

    @Test
    @Order(2)
    void socialHubAsksByItselfOnceForTheTextsOfWhatIsInProductionWithoutText() throws Exception {
        Flow f = flow();
        create("2026-12-01", "INSTAGRAM", "Carrusel", "Tres versos", "IN_PRODUCTION");
        create("2026-12-02", "TIKTOK", "Reel", "Leer en voz alta", "IN_PRODUCTION");
        create("2026-12-03", "INSTAGRAM", "Post", "Borrador sin texto", "DRAFT");
        // No se piden: una historia (no lleva texto), una que ya tiene texto, una propuesta y una cancelada
        create("2026-12-04", "INSTAGRAM", "Historia", "Historia sin texto", "IN_PRODUCTION");
        create("2026-12-05", "INSTAGRAM", "Post", "Con texto de Raúl", "IN_PRODUCTION");
        f.ok(f.put("/api/publications/2026-12-05-instagram", pub("2026-12-05", "INSTAGRAM", "Post", "Con texto de Raúl",
                "Lo escribí yo. Cuéntanos qué te parece.", "#eldelito")));
        create("2026-12-06", "INSTAGRAM", "Post", "Una propuesta", "PROPOSAL");
        create("2026-12-07", "INSTAGRAM", "Post", "Una cancelada", "IN_PRODUCTION");
        f.ok(f.post("/api/publications/2026-12-07-instagram/status-changes", "{\"status\":\"CANCELLED\",\"reason\":\"OTHER\"}"));

        assertThat(autoRequests.requestDue()).as("un solo encargo con las tres").isEqualTo(1);
        assertThat(autoRequests.requestDue()).as("no se repite").isZero();

        String task = captionsTask();
        f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "encargo de textos enviado");
        JsonNode request = f.request(task);
        assertThat(request.get("type").asString()).isEqualTo("socialhub.captions");
        List<String> ids = new ArrayList<>();
        request.get("inputs").get("publications").forEach(p -> ids.add(p.get("id").asString()));
        assertThat(ids).containsExactlyInAnyOrder("2026-12-01-instagram", "2026-12-02-tiktok", "2026-12-03-instagram");
        assertThat(request.get("inputs").get("brand_identity").get("call_to_action").asString()).isEqualTo("Cuéntanos qué te parece");
        // No lo ha pedido Raúl: sigue la regla normal de urgencia (las publicaciones están lejos)
        assertThat(request.get("urgent").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    void claudesTextsAreSavedAndRaulCorrectsOrDiscardsThemKeepingWhatHeTaught() throws Exception {
        Flow f = flow();
        String task = captionsTask();
        f.complete(task, """
                {"publications":[
                  {"id":"2026-12-01-instagram","caption":"Tres versos para tres noches. Cuéntanos qué te parece.","hashtags":"#eldelito #poesia"},
                  {"id":"2026-12-02-tiktok","caption":"Leer en voz alta cambia el poema.","hashtags":"#eldelito"},
                  {"id":"2026-12-03-instagram","caption":"Otro poema. Cuéntanos qué te parece.","hashtags":"#eldelito"}],
                 "summary":"Tono cercano"}""");
        assertThat(f.task(task).get("result").asString()).isEqualTo("3 textos guardados para revisar · Tono cercano");
        assertThat(f.publication("poemario", "2026-12-01-instagram").get("caption").asString()).startsWith("Tres versos para tres noches");
        assertThat(f.publication("poemario", "2026-12-02-tiktok").get("hashtags").asString()).isEqualTo("#eldelito");
        // Con texto ya no se vuelven a pedir, y los estados no se han movido solos
        assertThat(autoRequests.requestDue()).isZero();
        assertThat(f.status("poemario", "2026-12-01-instagram")).isEqualTo("IN_PRODUCTION");

        // Raúl corrige el primero: queda el texto de Claude y el suyo
        f.ok(f.put("/api/publications/2026-12-01-instagram", pub("2026-12-01", "INSTAGRAM", "Carrusel", "Tres versos",
                "Tres versos, tres noches. Cuéntanos qué te parece.", "#eldelito #poesia")));
        // Y descarta el segundo con motivo
        f.post("/api/publications/2026-12-02-tiktok/text-rejections", "{\"reason\":\"TONE\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-12-02-tiktok/text-rejections", "{\"reason\":\"TONE\"}").andExpect(status().isConflict());
        JsonNode memory = f.read("/api/brands/el-delito/editorial-memory");
        assertThat(memory.get("corrections").size()).isEqualTo(1);
        assertThat(memory.get("corrections").get(0).get("before_caption").asString())
                .isEqualTo("Tres versos para tres noches. Cuéntanos qué te parece.");
        assertThat(memory.get("corrections").get(0).get("after_caption").asString())
                .isEqualTo("Tres versos, tres noches. Cuéntanos qué te parece.");
        assertThat(memory.get("rejections").get(0).get("kind").asString()).isEqualTo("TEXT");
        assertThat(memory.get("rejections").get(0).get("content").asString()).startsWith("Leer en voz alta cambia el poema.");

        // Descartado y sin texto: SocialHub no vuelve a pedirlo solo (ya se pidió una vez); lo pide Raúl con el botón
        assertThat(autoRequests.requestDue()).isZero();
        String again = f.order("poemario", "CAPTIONS", "Textos otra vez", "2026-12-02-tiktok");
        JsonNode inMemory = f.request(again).get("inputs").get("editorial_memory");
        assertThat(inMemory.get("rejections").get(0).get("what").asString()).isEqualTo("texto descartado");
        assertThat(inMemory.get("rejections").get(0).get("reason").asString()).isEqualTo("Tono");
        assertThat(inMemory.get("corrections").get(0).get("raul_caption").asString()).isEqualTo("Tres versos, tres noches. Cuéntanos qué te parece.");
        assertThat(f.request(again).get("urgent").asBoolean()).as("lo pide Raúl: urgente").isTrue();
        f.complete(again, "{\"publications\":[{\"id\":\"2026-12-02-tiktok\",\"caption\":\"Más suave.\",\"hashtags\":\"#eldelito\"}]}");
        assertThat(f.publication("poemario", "2026-12-02-tiktok").get("caption").asString()).isEqualTo("Más suave.");
    }

    @Test
    @Order(4)
    void movingToReviewAsksForTheQualityReviewOnceAndAskAgainOnlyIfTheTextChanged() throws Exception {
        Flow f = flow();
        String id = "2026-12-01-instagram";
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(photo) + "}"));
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"DRAFT\"}").andExpect(status().isOk());
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"IN_REVIEW\"}").andExpect(status().isOk());

        java.util.Set<String> known = reviewIds();
        assertThat(autoRequests.requestDue()).isEqualTo(1);
        assertThat(autoRequests.requestDue()).as("el mismo texto no se revisa dos veces").isZero();
        String review = newReview(known);
        f.waitFor(() -> "WAITING".equals(f.task(review).get("status").asString()), "revisión enviada");
        assertThat(f.request(review).get("inputs").get("publication").get("caption").asString())
                .isEqualTo("Tres versos, tres noches. Cuéntanos qué te parece.");

        // Claude encuentra algo: bloquea hasta corregirlo o aceptarlo
        f.complete(review, "{\"issues\":[{\"message\":\"«noches» se repite\"}],\"summary\":\"Casi\"}");
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict());

        // Raúl cambia el texto y vuelve a revisión: se revisa otra vez, con el texto nuevo
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"DRAFT\"}").andExpect(status().isOk());
        f.ok(f.put("/api/publications/" + id, pub("2026-12-01", "INSTAGRAM", "Carrusel", "Tres versos",
                "Tres versos para tres lunas. Cuéntanos qué te parece.", "#eldelito #poesia")));
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"IN_REVIEW\"}").andExpect(status().isOk());
        known = reviewIds();
        assertThat(autoRequests.requestDue()).isEqualTo(1);
        String second = newReview(known);
        assertThat(second).isNotEqualTo(review);
        f.waitFor(() -> "WAITING".equals(f.task(second).get("status").asString()), "segunda revisión enviada");
        assertThat(f.request(second).get("inputs").get("publication").get("caption").asString()).contains("lunas");
        f.complete(second, "{\"issues\":[]}");
        // Un carrusel necesita al menos dos archivos: se acepta con motivo y pasa
        f.ok(f.post("/api/publications/" + id + "/quality-acceptances", "{\"key\":\"CAROUSEL_COUNT\",\"reason\":\"Carrusel de una foto\"}"));
        f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(f.status("poemario", id)).isEqualTo("APPROVED");
    }

    @Test
    @Order(5)
    void moreThanTenWithoutTextAreAskedInBatchesOfTen() throws Exception {
        Flow f = flow();
        for (int day = 10; day < 22; day++) create("2027-01-" + day, "INSTAGRAM", "Post", "Lote " + day, "IN_PRODUCTION");
        assertThat(autoRequests.requestDue()).isEqualTo(2);
        List<Integer> sizes = new ArrayList<>();
        for (JsonNode task : f.read("/api/tasks?station_id=gen").get("items")) {
            if (task.get("title").asString().startsWith("Textos de 1")) sizes.add(task.get("inputs").size());
            if (task.get("title").asString().equals("Textos de 2 publicaciones en producción")) sizes.add(task.get("inputs").size());
        }
        assertThat(sizes).containsExactlyInAnyOrder(10, 2);
    }


    @Test
    @Order(6)
    void claudesResultNeverOverwritesTheTextRaulWroteInTheMeantime() throws Exception {
        Flow f = flow();
        // Dos publicaciones sin texto en un mismo encargo: Raúl escribe el texto de la primera mientras Claude trabaja
        create("2027-02-01", "INSTAGRAM", "Post", "Con texto mientras tanto", "IN_PRODUCTION");
        create("2027-02-02", "INSTAGRAM", "Post", "Sigue sin texto", "IN_PRODUCTION");
        String task = f.order("poemario", "CAPTIONS", "Textos mientras escribo", "2027-02-01-instagram", "2027-02-02-instagram");
        f.ok(f.put("/api/publications/2027-02-01-instagram", pub("2027-02-01", "INSTAGRAM", "Post", "Con texto mientras tanto",
                "Mi texto, escrito a mano.", "#mio")));

        f.complete(task, """
                {"publications":[
                  {"id":"2027-02-01-instagram","caption":"Texto de Claude que no debe pisar.","hashtags":"#claude"},
                  {"id":"2027-02-02-instagram","caption":"Texto de Claude para la otra.","hashtags":"#eldelito"}]}""");

        // Lo de Raúl se queda tal cual; el de Claude queda a la vista en la incidencia para copiarlo si quiere
        JsonNode mine = f.publication("poemario", "2027-02-01-instagram");
        assertThat(mine.get("caption").asString()).isEqualTo("Mi texto, escrito a mano.");
        assertThat(mine.get("hashtags").asString()).isEqualTo("#mio");
        assertThat(mine.get("flag").asString()).contains("Texto de Claude que no debe pisar.").contains("#claude");
        // La otra sí recibe el texto de Claude, sin incidencia
        JsonNode other = f.publication("poemario", "2027-02-02-instagram");
        assertThat(other.get("caption").asString()).isEqualTo("Texto de Claude para la otra.");
        assertThat(other.has("flag") && !other.get("flag").isNull()).isFalse();
        assertThat(f.task(task).get("result").asString()).contains("1 texto guardado").contains("1 no se aplicó");

        // Si Raúl no toca el texto de Claude, un nuevo resultado de Claude sí lo sustituye (sigue siendo suyo)
        String again = f.order("poemario", "CAPTIONS", "Otra vez", "2027-02-02-instagram");
        f.complete(again, "{\"publications\":[{\"id\":\"2027-02-02-instagram\",\"caption\":\"Segunda de Claude.\",\"hashtags\":\"#eldelito\"}]}");
        assertThat(f.publication("poemario", "2027-02-02-instagram").get("caption").asString()).isEqualTo("Segunda de Claude.");
    }


    // ------------------------------------------------------------------ utilidades

    private static String pub(String day, String platform, String type, String title, String caption, String hashtags) {
        return """
                {"platform":"%s","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"19:00","caption":%s,"hashtags":%s}"""
                .formatted(platform, type, title, day, Flow.quote(caption), Flow.quote(hashtags));
    }

    private void create(String day, String platform, String type, String title, String status) throws Exception {
        flow().ok(flow().post("/api/promotions/poemario/publications", """
                {"platform":"%s","type":"%s","title":"%s","scheduled_on":"%s","scheduled_time":"19:00","initial_status":"%s"}"""
                .formatted(platform, type, title, day, status)));
    }

    /** La tarea del primer encargo automático de textos. */
    private String captionsTask() throws Exception {
        for (JsonNode t : flow().read("/api/tasks?station_id=gen").get("items")) {
            if ("AI_CAPTIONS".equals(t.get("type").asString())) return t.get("id").asString();
        }
        throw new AssertionError("No hay encargo de textos");
    }

    private java.util.Set<String> reviewIds() throws Exception {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode t : flow().read("/api/tasks?station_id=rev").get("items")) {
            if ("AI_QUALITY_REVIEW".equals(t.get("type").asString())) ids.add(t.get("id").asString());
        }
        return ids;
    }

    /** La revisión de calidad que no estaba antes. */
    private String newReview(java.util.Set<String> known) throws Exception {
        java.util.Set<String> now = reviewIds();
        now.removeAll(known);
        assertThat(now).hasSize(1);
        return now.iterator().next();
    }
}
