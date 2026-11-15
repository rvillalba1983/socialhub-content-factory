package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo «Edición de marca» (manual, §8), de extremo a extremo: se edita la ficha (nombre, usuario y color), se crean sus
 * carpetas, se guarda la identidad y se anotan decisiones editoriales. Y se comprueba que todo eso llega a donde tiene
 * que llegar: al mapa, a «Qué hacer ahora», al control de calidad de las publicaciones y a los encargos a Claude.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrandEditFlowTest {

    @TempDir static Path drive;
    static Path root;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, root.resolve("encargos"));
        return flow;
    }

    private static final String IDENTITY = """
            {"tone":" Cercano y con humor ","audience":"Lectores de poesía","forbidden_topics":["política"," "],
             "preferred_expressions":["de aquí a la luna"],"forbidden_expressions":["compra ya","COMPRA YA","lo mejor del mercado"],
             "colors":["#D23A2E"],"font":"Fraunces","logo":"","visual_style":"Colores planos","photo_style":"",
             "call_to_action":"Cuéntanos qué te parece","fixed_hashtags":"#eldelito #poesia"}""";

    @Test
    @Order(1)
    void aBrandIsEditedAndTheChangeShowsEverywhereWithoutTouchingTheOtherBrandOrItsPromotions() throws Exception {
        root = drive.resolve("socialhub/eldelito");
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/brands", "{\"name\":\"Litoques\",\"handle\":\"@litoques\",\"color\":\"#C98A1B\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE"}"""));

        JsonNode edited = f.ok(f.put("/api/brands/el-delito", "{\"name\":\"El DeLito (poemario)\",\"handle\":\"@eldelito\",\"color\":\"#AA3322\"}"));
        assertThat(edited.get("id").asString()).as("el identificador no cambia").isEqualTo("el-delito");
        assertThat(edited.get("name").asString()).isEqualTo("El DeLito (poemario)");

        // En el mapa y en la lista de marcas, con el nuevo nombre, usuario y color
        JsonNode factory = f.read("/api/factory");
        JsonNode brand = brand(factory, "el-delito");
        assertThat(brand.get("name").asString()).isEqualTo("El DeLito (poemario)");
        assertThat(brand.get("handle").asString()).isEqualTo("@eldelito");
        assertThat(brand.get("color").asString()).isEqualTo("#AA3322");
        assertThat(brand(factory, "litoques").get("name").asString()).isEqualTo("Litoques");
        assertThat(brand(factory, "litoques").get("handle").asString()).isEqualTo("@litoques");
        assertThat(f.read("/api/brands").get("items").size()).isEqualTo(2);
        // La promoción de la marca sigue siendo suya
        assertThat(f.read("/api/promotions/poemario").get("brand_id").asString()).isEqualTo("el-delito");

        // Lo que no vale no cambia nada
        f.put("/api/brands/el-delito", "{\"name\":\"X\",\"handle\":\"@x\",\"color\":\"rojo\"}").andExpect(status().isBadRequest());
        f.put("/api/brands/el-delito", "{\"name\":\"\",\"handle\":\"@x\",\"color\":\"#000000\"}").andExpect(status().isBadRequest());
        f.put("/api/brands/no-existe", "{\"name\":\"X\",\"handle\":\"@x\",\"color\":\"#000000\"}").andExpect(status().isNotFound());
        assertThat(brand(f.read("/api/factory"), "el-delito").get("name").asString()).isEqualTo("El DeLito (poemario)");
    }

    @Test
    @Order(2)
    void creatingTheFoldersFromTheRootSettlesTheBrandNoticeAndThePromotionInheritsThem() throws Exception {
        Flow f = flow();
        assertThat(recommendation("BRAND_FOLDERS_MISSING", "el-delito")).as("sin carpetas, «Qué hacer ahora» lo pide").isNotNull();

        f.ok(f.post("/api/brands/el-delito/folders/creation", "{\"base\":" + f.q(root) + "}"));
        for (String name : List.of("borradores", "generados", "aprobados", "publicados", "informes", "encargos")) {
            assertThat(root.resolve(name)).isDirectory();
        }
        for (String name : List.of("para-claude", "para-claude-pc", "de-claude", "publico")) {
            assertThat(root.resolve("encargos").resolve(name)).isDirectory();
        }
        assertThat(recommendation("BRAND_FOLDERS_MISSING", "el-delito")).as("con las carpetas, ya no").isNull();
        // La otra marca sigue sin ellas
        assertThat(recommendation("BRAND_FOLDERS_MISSING", "litoques")).isNotNull();

        // La promoción las hereda
        JsonNode folders = f.read("/api/promotions/poemario/folders");
        assertThat(folders.get("items").size()).isEqualTo(7);
        for (JsonNode folder : folders.get("items")) {
            assertThat(folder.get("origin").asString()).isEqualTo("BRAND");
            assertThat(folder.get("available").asBoolean()).isTrue();
        }

        // «publico» compartida a mano: se marca y se desmarca
        assertThat(f.read("/api/brands/el-delito/public-share").get("shared").asBoolean()).isFalse();
        assertThat(f.ok(f.put("/api/brands/el-delito/public-share", "{\"shared\":true}")).get("shared").asBoolean()).isTrue();
        assertThat(f.ok(f.put("/api/brands/el-delito/public-share", "{\"shared\":false}")).get("shared").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    void theIdentityIsSavedCleanAndReachesTheQualityControlAndTheOrdersToClaude() throws Exception {
        Flow f = flow();
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        JsonNode saved = f.ok(f.put("/api/brands/el-delito/identity", IDENTITY));
        assertThat(saved.get("tone").asString()).isEqualTo("Cercano y con humor");
        assertThat(strings(saved.get("forbidden_topics"))).containsExactly("política");
        assertThat(strings(saved.get("forbidden_expressions"))).as("sin repetidos").containsExactly("compra ya", "COMPRA YA", "lo mejor del mercado");

        // Una publicación con texto que rompe la identidad
        f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Versos","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "initial_status":"IN_PRODUCTION"}"""));
        f.ok(f.put("/api/publications/2026-12-01-instagram", """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Versos","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "caption":"Compra ya el libro","hashtags":"#eldelito"}"""));
        assertThat(qualityKeys("2026-12-01-instagram")).contains("FORBIDDEN:compra ya", "CTA_MISSING", "HASHTAG_MISSING:#poesia")
                .doesNotContain("HASHTAG_MISSING:#eldelito");

        // El encargo a Claude lleva la identidad ya limpia y solo con los campos rellenos
        String task = f.order("poemario", "CAPTIONS", "Textos", "2026-12-01-instagram");
        JsonNode inIdentity = f.request(task).get("inputs").get("brand_identity");
        assertThat(inIdentity.get("tone").asString()).isEqualTo("Cercano y con humor");
        assertThat(strings(inIdentity.get("forbidden_expressions"))).contains("compra ya");
        assertThat(inIdentity.get("call_to_action").asString()).isEqualTo("Cuéntanos qué te parece");
        assertThat(inIdentity.has("logo")).as("lo vacío no va").isFalse();
        assertThat(inIdentity.has("photo_style")).isFalse();

        // Se edita la identidad: sin la expresión prohibida y con otra llamada a la acción, los avisos cambian al instante
        f.ok(f.put("/api/brands/el-delito/identity", IDENTITY
                .replace("\"compra ya\",\"COMPRA YA\",", "").replace("Cuéntanos qué te parece", "Compra ya el libro")));
        assertThat(qualityKeys("2026-12-01-instagram")).doesNotContain("FORBIDDEN:compra ya", "CTA_MISSING")
                .contains("HASHTAG_MISSING:#poesia");
        // Y los encargos siguientes llevan la identidad nueva
        String second = f.order("poemario", "CAPTIONS", "Textos otra vez", "2026-12-01-instagram");
        assertThat(f.request(second).get("inputs").get("brand_identity").get("call_to_action").asString()).isEqualTo("Compra ya el libro");
        // La otra marca no la comparte
        assertThat(f.read("/api/brands/litoques/identity").get("tone").asString()).isEmpty();
    }

    @Test
    @Order(4)
    void editorialDecisionsAreAddedListedDeletedAndGoInEveryOrder() throws Exception {
        Flow f = flow();
        JsonNode first = f.ok(f.post("/api/brands/el-delito/decisions", "{\"text\":\"  Nada de precios en los textos  \"}"));
        assertThat(first.get("text").asString()).isEqualTo("Nada de precios en los textos");
        JsonNode second = f.ok(f.post("/api/brands/el-delito/decisions", "{\"text\":\"Siempre en segunda persona\"}"));
        assertThat(f.read("/api/brands/el-delito/decisions").get("items").size()).isEqualTo(2);
        f.post("/api/brands/el-delito/decisions", "{\"text\":\"   \"}").andExpect(status().isBadRequest());
        f.post("/api/brands/no-existe/decisions", "{\"text\":\"x\"}").andExpect(status().isNotFound());

        // Van a la memoria editorial y a cada encargo
        List<String> inMemory = strings(f.read("/api/brands/el-delito/editorial-memory").get("decisions").findValues("text"));
        assertThat(inMemory).containsExactly("Nada de precios en los textos", "Siempre en segunda persona");
        String task = f.order("poemario", "RESEARCH", "Ideas", "Granada");
        assertThat(strings(f.request(task).get("inputs").get("editorial_memory").get("decisions")))
                .containsExactly("Nada de precios en los textos", "Siempre en segunda persona");

        // Se borra una
        f.delete("/api/brands/el-delito/decisions/" + first.get("id").asLong()).andExpect(status().isNoContent());
        f.delete("/api/brands/el-delito/decisions/" + first.get("id").asLong()).andExpect(status().isNotFound());
        assertThat(f.read("/api/brands/el-delito/decisions").get("items").size()).isEqualTo(1);
        assertThat(f.read("/api/brands/el-delito/decisions").get("items").get(0).get("id").asLong()).isEqualTo(second.get("id").asLong());
        // Las decisiones son de la marca, no de la otra
        assertThat(f.read("/api/brands/litoques/decisions").get("items").size()).isZero();
        assertThat(Files.isDirectory(root.resolve("encargos/para-claude"))).isTrue();
    }

    // ------------------------------------------------------------------ utilidades

    private static JsonNode brand(JsonNode factory, String id) {
        for (JsonNode b : factory.get("brands")) if (b.get("id").asString().equals(id)) return b;
        throw new AssertionError("No hay la marca " + id);
    }

    private JsonNode recommendation(String kind, String brandId) throws Exception {
        for (JsonNode r : flow().read("/api/factory").get("recommendations")) {
            if (kind.equals(r.get("kind").asString()) && r.get("id").asString().endsWith(":" + brandId)) return r;
        }
        return null;
    }

    private List<String> qualityKeys(String publicationId) throws Exception {
        List<String> keys = new ArrayList<>();
        flow().read("/api/publications/" + publicationId + "/quality").get("issues").forEach(i -> keys.add(i.get("key").asString()));
        return keys;
    }

    private static List<String> strings(Iterable<JsonNode> nodes) {
        List<String> list = new ArrayList<>();
        nodes.forEach(n -> list.add(n.asString()));
        return list;
    }
}
