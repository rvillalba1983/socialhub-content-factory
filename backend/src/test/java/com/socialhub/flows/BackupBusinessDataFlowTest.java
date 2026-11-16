package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppModeHolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Copia y restauración con datos de negocio» (manual, §10): marca, promoción y publicación reales (con texto y
 * archivo) → copia → cambios (se edita la marca y el texto, nace otra promoción con otra publicación) → restaurar →
 * vuelve exactamente lo de la copia, lo posterior desaparece, las secuencias siguen avanzando y SocialHub queda parado.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupBusinessDataFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path file;
    static String backup;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @AfterAll
    static void resume() { AppModeHolder.resume(); }

    @Test
    @Order(1)
    void theBusinessDataIsBackedUp() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        file = Files.writeString(Files.createDirectories(drive.resolve("DeLito/aprobados")).resolve("pieza.txt"), "pieza");
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(file.getParent()) + "}]}"));
        f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Tres versos","scheduled_on":"2026-12-01","scheduled_time":"19:00",
                 "initial_status":"IN_PRODUCTION","caption":"Texto original de la copia","hashtags":"#poesia"}"""));

        JsonNode created = f.ok(f.post("/api/backups", "{}"));
        backup = created.get("name").asString();
        assertThat(created.get("kind").asString()).isEqualTo("MANUAL");
        assertThat(f.read("/api/backups").get("items").get(0).get("name").asString()).isEqualTo(backup);
    }

    @Test
    @Order(2)
    void changesAfterTheBackupAreMadeThroughTheApi() throws Exception {
        Flow f = flow();
        f.ok(f.put("/api/brands/el-delito", "{\"name\":\"El DeLito CAMBIADO\",\"handle\":\"@otro\",\"color\":\"#000000\"}"));
        JsonNode edited = f.ok(f.put("/api/publications/2026-12-01-instagram", """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Título cambiado","scheduled_on":"2026-12-02","scheduled_time":"20:00",
                 "caption":"Texto cambiado después","hashtags":"#otro"}"""));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Otra","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        assertThat(f.read("/api/brands").get("items").get(0).get("name").asString()).isEqualTo("El DeLito CAMBIADO");
        assertThat(f.publication("poemario", edited.get("id").asString()).get("caption").asString()).isEqualTo("Texto cambiado después");
    }

    @Test
    @Order(3)
    void restoringBringsBackExactlyWhatTheBackupHadAndLeavesSocialHubStopped() throws Exception {
        Flow f = flow();
        // Sin escribir bien el nombre no se restaura y nada cambia
        f.post("/api/backups/restorations", "{\"name\":" + f.q(backup) + ",\"confirmation\":\"otra\"}").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertThat(f.read("/api/brands").get("items").get(0).get("name").asString()).isEqualTo("El DeLito CAMBIADO");

        JsonNode restored = f.ok(f.post("/api/backups/restorations", "{\"name\":" + f.q(backup) + ",\"confirmation\":" + f.q(backup) + "}"));
        assertThat(restored.get("restored").asString()).isEqualTo(backup);
        assertThat(restored.get("safety_backup").asString()).endsWith("-antes-de-restaurar.zip");

        // Lo de negocio es lo de la copia
        JsonNode brands = f.read("/api/brands").get("items");
        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).get("name").asString()).isEqualTo("El DeLito");
        assertThat(brands.get(0).get("handle").asString()).isEqualTo("@elde_lito");
        List<String> promotions = new java.util.ArrayList<>();
        f.read("/api/promotions").get("items").forEach(p -> promotions.add(p.get("id").asString()));
        assertThat(promotions).containsExactly("poemario");
        JsonNode publication = f.publication("poemario", "2026-12-01-instagram");
        assertThat(publication.get("title").asString()).isEqualTo("Tres versos");
        assertThat(publication.get("caption").asString()).isEqualTo("Texto original de la copia");
        assertThat(publication.get("hashtags").asString()).isEqualTo("#poesia");
        assertThat(publication.get("status").asString()).isEqualTo("IN_PRODUCTION");
        assertThat(f.read("/api/promotions/poemario/publications").get("items")).hasSize(1);
        assertThat(f.read("/api/brands/el-delito/folders").get("items").toString()).contains("aprobados");

        // SocialHub queda parado: no acepta cambios y la interfaz lo sabe
        assertThat(f.read("/api/app-mode").get("halted_reason").asString()).contains(backup);
        f.post("/api/backups", "{}").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
        f.put("/api/brands/el-delito", "{\"name\":\"X\",\"handle\":\"@x\",\"color\":\"#000000\"}").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
    }
}
