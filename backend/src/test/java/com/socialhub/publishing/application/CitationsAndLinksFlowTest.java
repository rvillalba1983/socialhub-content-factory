package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.flows.Flow;
import com.socialhub.flows.FlowClock;
import com.socialhub.flows.FlowTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Citas y enlaces en el control de calidad, de extremo a extremo: Claude devuelve textos con citas; SocialHub las busca
 * en los documentos de la promoción (txt y PDF con PDFBox) y avisa si no aparecen o no se pueden leer; los enlaces y el
 * texto ya usado solo informan.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CitationsAndLinksFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path outside;

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
        Path material = Files.createDirectories(drive.resolve("DeLito/material"));
        Path info = Files.createDirectories(material.resolve("info"));
        Files.writeString(info.resolve("poemario.txt"), "Primer poema\nla noche se abre\ncomo una herida\nde luz y de sal\n");
        pdf(info.resolve("poemario.pdf"), "Portada del libro", "Cuando el mar \"calla\" y el cielo se queda sin nombre");
        pdf(info.resolve("escaneado.pdf"), "");
        Files.writeString(info.resolve("carta.docx"), "no es un docx de verdad");
        outside = Files.writeString(drive.resolve("secreto.txt"), "esto no es de la promoción");
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Antología","starts_on":"2027-02-01","ends_on":"2027-03-31","status":"PLANNED"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"MATERIAL\",\"location\":" + f.q(material) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
    }

    @Test
    @Order(2)
    void citationsAreCheckedAgainstTheDocumentsAndNothingIsEverVerifiedByGuessing() throws Exception {
        Flow f = flow();
        create("poemario", "2026-12-01");
        String task = f.order("poemario", "CAPTIONS", "Textos", "2026-12-01-instagram");
        f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "encargo de textos enviado");
        // El encargo lleva las reglas de citas y de preguntas
        List<String> rules = new ArrayList<>();
        f.request(task).get("rules").forEach(r -> rules.add(r.asString()));
        assertThat(rules).anyMatch(r -> r.contains("LITERAL") && r.contains("citations"));
        assertThat(rules).anyMatch(r -> r.contains("needs_human") && r.contains("options"));

        f.complete(task, """
                {"publications":[{"id":"2026-12-01-instagram",
                  "caption":"Un verso. Mira https://127.0.0.1/x y http://litoques.es/a, o https://127.0.0.1/x. Cuéntanos qué te parece.",
                  "hashtags":"#eldelito",
                  "citations":[
                    {"text":"la noche   se abre\\ncomo una HERIDA","source_file":"info/poemario.txt","location":"línea 2"},
                    {"text":"la noche   se abre\\ncomo una herida","source_file":"info/poemario.txt","location":"línea 2"},
                    {"text":"Esto no está en el libro","source_file":"info/poemario.txt"},
                    {"text":"Cuando el mar “calla” y el cielo","source_file":"info/poemario.pdf"},
                    {"text":"cualquier cosa","source_file":"info/escaneado.pdf","location":"p. 4"},
                    {"text":"otra","source_file":"info/carta.docx"},
                    {"text":"esto no es de la promoción","source_file":%s},
                    {"text":"esto no es de la promoción","source_file":"../../secreto.txt"},
                    {"text":"algo","source_file":"info/no-existe.txt"}]}]}"""
                .formatted(Flow.quote(outside.toString())));

        // Los enlaces se comprueban en segundo plano: el primer aviso dice «comprobándolo»
        f.waitFor(() -> f.read("/api/publications/2026-12-01-instagram/quality").toString().contains("direcciones privadas ni locales"),
                "enlace local comprobado en segundo plano");
        JsonNode report = f.read("/api/publications/2026-12-01-instagram/quality");
        List<String> blocking = new ArrayList<>();
        List<String> info = new ArrayList<>();
        report.get("issues").forEach(i -> {
            String line = i.get("key").asString() + " | " + i.get("message").asString();
            if ("INFO".equals(i.path("level").asString("BLOCKING"))) info.add(line); else blocking.add(line);
        });
        // La cita en txt (con espacios y saltos distintos) y la del PDF (con comillas tipográficas) se verifican; la que cambia una mayúscula no
        assertThat(info).anyMatch(l -> l.startsWith("CITATION:") && l.contains("Cita verificada en «poemario.txt», línea 2"));
        assertThat(info).anyMatch(l -> l.startsWith("CITATION:") && l.contains("Cita verificada en «poemario.pdf», página 2"));
        assertThat(blocking).anyMatch(l -> l.contains("Cita no encontrada en el documento «poemario.txt»") && l.contains("HERIDA"));
        assertThat(blocking).anyMatch(l -> l.contains("Cita no encontrada en el documento «poemario.txt»") && l.contains("Esto no está en el libro"));
        // Documento ilegible o sin soporte: «sin verificar», nunca «verificada»
        assertThat(blocking).anyMatch(l -> l.contains("Sin verificar: comprueba la cita en escaneado.pdf, p. 4"));
        assertThat(blocking).anyMatch(l -> l.contains("Sin verificar: comprueba la cita en carta.docx, ubicación no indicada"));
        assertThat(info).noneMatch(l -> l.contains("escaneado") || l.contains("carta.docx"));
        // Fuera de las carpetas de la promoción o inexistente: nunca se lee
        assertThat(blocking).filteredOn(l -> l.contains("no está en las carpetas de la promoción")).hasSize(2);
        assertThat(blocking).anyMatch(l -> l.contains("no existe «info/no-existe.txt»"));
        assertThat(String.join("\n", info) + String.join("\n", blocking)).doesNotContain("esto no es de la promoción\n");

        // Los enlaces solo informan: uno a una dirección local no se comprueba, y uno http tampoco
        assertThat(info).anyMatch(l -> l.startsWith("LINK:https://127.0.0.1/x") && l.contains("No comprobado")
                && l.contains("direcciones privadas ni locales"));
        assertThat(info).anyMatch(l -> l.startsWith("LINK:http://litoques.es/a") && l.contains("solo se comprueban enlaces https"));
        assertThat(info).filteredOn(l -> l.startsWith("LINK:https://127.0.0.1/x")).as("sin repetir").hasSize(1);
        assertThat(blocking).noneMatch(l -> l.startsWith("LINK:"));

        // open cuenta solo los que bloquean; un aviso informativo no se puede aceptar
        assertThat(report.get("issues").findValues("accepted")).noneMatch(JsonNode::asBoolean);
        assertThat(report.get("open").asInt()).isEqualTo(blocking.size());
        f.post("/api/publications/2026-12-01-instagram/quality-acceptances", "{\"key\":\"LINK:https://127.0.0.1/x\",\"reason\":\"da igual\"}")
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(3)
    void aCitationCanBeAcceptedWithAReasonAndAFixedOneStopsWarning() throws Exception {
        Flow f = flow();
        JsonNode report = f.read("/api/publications/2026-12-01-instagram/quality");
        String key = null;
        for (JsonNode i : report.get("issues")) if (i.get("message").asString().startsWith("Sin verificar: comprueba la cita en escaneado.pdf")) key = i.get("key").asString();
        assertThat(key).isNotNull();
        JsonNode after = f.ok(f.post("/api/publications/2026-12-01-instagram/quality-acceptances",
                "{\"key\":" + Flow.quote(key) + ",\"reason\":\"Comprobada a mano en el PDF\"}"));
        assertThat(after.get("open").asInt()).isEqualTo(report.get("open").asInt() - 1);
    }

    @Test
    @Order(4)
    void theSameTextOfAnotherPromotionOfTheBrandIsReportedWithItsDateAndDoesNotBlock() throws Exception {
        Flow f = flow();
        f.ok(f.put("/api/publications/2026-12-01-instagram", """
                {"platform":"INSTAGRAM","type":"Post","title":"Original","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "caption":"Texto  que se repite.\\nSin cambios","hashtags":""}"""));
        f.ok(f.post("/api/promotions/antologia/publications", """
                {"platform":"INSTAGRAM","type":"Post","title":"Repetida","scheduled_on":"2027-02-10","scheduled_time":"19:00",
                 "caption":"texto que se repite. sin cambios","hashtags":""}"""));
        JsonNode report = f.read("/api/publications/2027-02-10-instagram/quality");
        JsonNode reused = null;
        for (JsonNode i : report.get("issues")) if (i.get("key").asString().startsWith("REUSED:")) reused = i;
        assertThat(reused).isNotNull();
        assertThat(reused.get("level").asString()).isEqualTo("INFO");
        assertThat(reused.get("message").asString()).startsWith("Este texto ya se usó el 1 dic 2026").contains("2026-12-01-instagram");
        // No aparece en la propia publicación original (la repetida es posterior en otra promoción: también informa, pero con su fecha)
        assertThat(f.read("/api/publications/2026-12-01-instagram/quality").get("issues").findValues("key").stream()
                .map(JsonNode::asString)).contains("REUSED:2027-02-10-instagram");
    }

    // ------------------------------------------------------------------ utilidades

    private void create(String promotion, String day) throws Exception {
        flow().ok(flow().post("/api/promotions/" + promotion + "/publications", """
                {"platform":"INSTAGRAM","type":"Post","title":"Poema","scheduled_on":"%s","scheduled_time":"19:00","initial_status":"IN_PRODUCTION"}"""
                .formatted(day)));
    }

    /** Un PDF con una página por cada texto (una página vacía si el texto es vacío: como un escaneado). */
    private static void pdf(Path file, String... pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text.isEmpty()) continue;
                try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                    out.beginText();
                    out.setFont(font, 12);
                    out.newLineAtOffset(50, 700);
                    out.showText(text);
                    out.endText();
                }
            }
            doc.save(file.toFile());
        }
    }
}
