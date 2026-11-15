package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Flujo «Importar identidad desde .txt → previsualizar → guardar» (manual, §8). El .txt lo lee y lo previsualiza el
 * frontal; su prueba ({@code identityExample.test.ts}) comprueba que el ejemplo del manual da exactamente
 * {@code flows/identidad-marca.json}. Aquí está la otra mitad, contra el backend real: ese JSON se guarda por la API y
 * queda idéntico, lo que no cabe en el contrato se rechaza sin guardar nada, y lo importado llega al control de calidad
 * y a los encargos a Claude.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityImportFlowTest {

    @TempDir static Path drive;
    static Path orders;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    private ObjectNode imported() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/flows/identidad-marca.json")) {
            return (ObjectNode) flow().json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @Order(1)
    void whatTheImportProducesIsSavedIdenticalAndComesBackTheSame() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));

        // Marca nueva: sin identidad
        assertThat(f.read("/api/brands/el-delito/identity").get("tone").asString()).isEmpty();

        JsonNode saved = f.ok(f.put("/api/brands/el-delito/identity", imported().toString()));
        assertThat(saved).isEqualTo(imported());
        assertThat(f.read("/api/brands/el-delito/identity")).as("lo que se lee después").isEqualTo(imported());
    }

    @Test
    @Order(2)
    void anythingOverTheContractLimitIsRejectedAndNothingIsSaved() throws Exception {
        Flow f = flow();
        // Cada uno de los máximos del formato versión 1, un paso por encima
        List<String> overLimit = List.of(
                with("tone", "a".repeat(2001)),
                with("audience", "a".repeat(2001)),
                with("font", "a".repeat(201)),
                with("logo", "a".repeat(501)),
                with("visual_style", "a".repeat(2001)),
                with("photo_style", "a".repeat(2001)),
                with("call_to_action", "a".repeat(501)),
                with("fixed_hashtags", "#" + "a".repeat(1000)),
                list("forbidden_topics", 51, 10),
                list("preferred_expressions", 101, 10),
                list("forbidden_expressions", 101, 10),
                list("colors", 21, 10),
                list("forbidden_topics", 1, 201),
                list("colors", 1, 41));
        List<String> notRejected = new ArrayList<>();
        for (int i = 0; i < overLimit.size(); i++) {
            int code = f.put("/api/brands/el-delito/identity", overLimit.get(i)).andReturn().getResponse().getStatus();
            if (code != 400) notRejected.add("caso " + i + " → " + code);
        }
        assertThat(notRejected).as("máximos que el backend no hace cumplir").isEmpty();
        // Un campo que no existe en el formato lo avisa el frontal al leer el .txt; la API lo ignora y no lo guarda
        assertThat(f.ok(f.put("/api/brands/el-delito/identity", imported().put("campo_inventado", "x").toString())).has("campo_inventado"))
                .isFalse();
        f.put("/api/brands/no-existe/identity", imported().toString()).andExpect(status().isNotFound());
        assertThat(f.read("/api/brands/el-delito/identity")).as("sigue la identidad de antes").isEqualTo(imported());

        // Justo en el máximo, sí cabe; y se puede volver a lo anterior
        ObjectNode atTheLimit = imported();
        atTheLimit.put("tone", "a".repeat(2000)).put("font", "b".repeat(200)).put("logo", "c".repeat(500));
        var colors = atTheLimit.putArray("colors");
        for (int i = 0; i < 20; i++) colors.add("d".repeat(40));
        JsonNode accepted = f.ok(f.put("/api/brands/el-delito/identity", atTheLimit.toString()));
        assertThat(accepted.get("tone").asString()).hasSize(2000);
        // Los repetidos se quitan: los 20 colores iguales quedan en uno
        assertThat(accepted.get("colors").size()).isEqualTo(1);
        f.ok(f.put("/api/brands/el-delito/identity", imported().toString()));
    }

    @Test
    @Order(3)
    void fieldsThatDidNotComeInTheFileKeepTheirValueWhenThePreviewIsSaved() throws Exception {
        Flow f = flow();
        // La vista previa del frontal parte de la identidad guardada y solo cambia los campos que vienen en el .txt:
        // lo que se guarda es la identidad completa con esos campos cambiados
        ObjectNode merged = (ObjectNode) f.read("/api/brands/el-delito/identity");
        merged.put("tone", "Más serio");
        merged.putArray("colors");
        JsonNode saved = f.ok(f.put("/api/brands/el-delito/identity", merged.toString()));
        assertThat(saved.get("tone").asString()).isEqualTo("Más serio");
        assertThat(saved.get("colors").size()).as("un campo con «##» pero vacío se queda vacío").isZero();
        assertThat(saved.get("audience").asString()).isEqualTo(imported().get("audience").asString());
        assertThat(saved.get("fixed_hashtags").asString()).isEqualTo("#eldelito #litoques");
        f.ok(f.put("/api/brands/el-delito/identity", imported().toString()));
    }

    @Test
    @Order(4)
    void theImportedIdentityDecidesTheQualityNoticesAndTravelsInEveryOrder() throws Exception {
        Flow f = flow();
        f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Versos","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "initial_status":"IN_PRODUCTION"}"""));
        String id = "2026-12-01-instagram";

        // Con la expresión prohibida («Compra ya», sin tilde ni mayúsculas que valgan), sin la llamada a la acción y con un hashtag fijo
        update(id, "¡COMPRA YA el poemario!", "#eldelito");
        assertThat(qualityKeys(id)).contains("FORBIDDEN:compra ya", "CTA_MISSING", "HASHTAG_MISSING:#litoques")
                .doesNotContain("HASHTAG_MISSING:#eldelito");

        // Cumpliendo la identidad al pie de la letra no queda ningún aviso de texto
        update(id, "Un poema nuevo. Cuéntanos qué te parece en los comentarios.", "#eldelito #litoques");
        assertThat(qualityKeys(id)).noneMatch(k -> k.startsWith("FORBIDDEN") || k.startsWith("CTA") || k.startsWith("HASHTAG") || k.startsWith("TEXT"));

        // Y el encargo a Claude lleva los doce campos tal como se importaron
        String task = f.order("poemario", "CAPTIONS", "Textos", id);
        JsonNode inRequest = f.request(task).get("inputs").get("brand_identity");
        assertThat(inRequest).isEqualTo(imported());
        assertThat(inRequest.size()).isEqualTo(12);
    }

    // ------------------------------------------------------------------ utilidades

    private void update(String id, String caption, String hashtags) throws Exception {
        flow().ok(flow().put("/api/publications/" + id, """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Versos","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "caption":%s,"hashtags":%s}""".formatted(flow().q(caption), flow().q(hashtags))));
    }

    private List<String> qualityKeys(String publicationId) throws Exception {
        List<String> keys = new ArrayList<>();
        flow().read("/api/publications/" + publicationId + "/quality").get("issues").forEach(i -> keys.add(i.get("key").asString()));
        return keys;
    }

    private String with(String field, String value) throws IOException {
        return imported().put(field, value).toString();
    }

    private String list(String field, int items, int length) throws IOException {
        ObjectNode body = imported();
        var array = body.putArray(field);
        for (int i = 0; i < items; i++) array.add(("x" + i + "-" + "y".repeat(length)).substring(0, length));
        return body.toString();
    }
}
