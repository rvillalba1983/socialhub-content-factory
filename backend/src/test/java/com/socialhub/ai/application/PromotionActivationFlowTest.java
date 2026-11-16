package com.socialhub.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.flows.Flow;
import com.socialhub.flows.FlowClock;
import com.socialhub.flows.FlowTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Activación automática de promoción» (manual, §3 y §7), de extremo a extremo y con la base de datos real:
 * una promoción planificada que empieza hoy pasa sola a activa en la ronda, conserva todo lo que tenía, se avisa en
 * «Qué hacer ahora» ese día y solo ese día, y sus propuestas de la primera semana se piden una sola vez.
 * Las que empiezan más tarde, las ya terminadas, las pausadas y las activas no se tocan.
 * El reloj es el de {@link FlowClock}: martes 10 nov 2026 a las 10:00 de Madrid.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PromotionActivationFlowTest {

    @TempDir static Path drive;
    static Path orders;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;
    @Autowired AiAutoOrders autoOrders;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    private static final String FUTURA = """
            {"brand_id":"el-delito","name":"Futura","starts_on":"2026-11-16","ends_on":"2027-01-31","status":"PLANNED",
             "description":"Lanzamiento del segundo poemario","scope":"Instagram y TikTok","cadence":"3 a la semana",
             "metrics_source":"Publer","goals":["Mil lecturas","Cien reseñas"],"rules":["Nada de precios"],
             "human_todos":["Enviar los ejemplares"],
             "milestones":[{"starts_on":"2026-11-21","ends_on":"2026-11-23","label":"Días gratis"}],
             "resources":[{"kind":"DRIVE","location":"G:\\\\Mi unidad\\\\Futura","description":"Material"}],
             "automations":[{"name":"Tanda semanal","schedule":"Lunes 9:00","model":"SONNET","runs_on":"CLOUD","status":"OK"}],
             "routes":[{"name":"Publer","status":"OK","label":"Principal","notes":"Con Chrome"}],
             "settings":[{"label":"Hora","value":"19:00","origin":"PROMOTION"}]}""";

    @Test
    @Order(1)
    void setup() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
    }

    @Test
    @Order(2)
    void thePromotionsAreSavedAndEachSaveSaysWhenItsProposalsWillBeAsked() throws Exception {
        Flow f = flow();
        // Empieza el lunes que viene: el lunes pasado a las 18:00 ya tocaba pedir su primera semana, y se pide al guardar
        JsonNode futura = f.ok(f.post("/api/promotions", FUTURA));
        assertThat(futura.get("id").asString()).isEqualTo("futura");
        assertThat(futura.get("status").asString()).isEqualTo("PLANNED");
        assertThat(futura.get("proposals_note").asString()).startsWith("Propuestas de la primera semana pedidas");

        // Empieza dentro de tres semanas: aún no toca pedirlas y el guardado dice cuándo
        JsonNode tardia = f.ok(f.post("/api/promotions", promotion("Tardia", "2026-11-30", "2027-02-01", "PLANNED")));
        assertThat(tardia.get("proposals_note").asString()).isEqualTo("Sus propuestas se pedirán el lunes 23 de nov a las 18:00");

        // Las que no empiezan ahora: ya terminada, pausada y una ya activa
        f.ok(f.post("/api/promotions", promotion("Acabada", "2026-10-01", "2026-11-05", "PLANNED")));
        f.ok(f.post("/api/promotions", promotion("Pausada", "2026-11-01", "2027-02-01", "PAUSED")));
        f.ok(f.post("/api/promotions", promotion("Activa", "2026-11-02", "2027-02-01", "ACTIVE")));
        assertThat(status("futura")).isEqualTo("PLANNED");

        // La primera semana de «Futura» se pidió una sola vez, con el lunes 16 como semana
        f.waitFor(() -> proposalOrders("futura").size() == 1, "propuestas de la primera semana enviadas");
        assertThat(proposalOrders("futura").getFirst().get("inputs").get("items").get(0).asString()).isEqualTo("2026-11-16");
    }

    @Test
    @Order(3)
    void beforeTheStartDayNothingIsActivated() throws Exception {
        assertThat(autoOrders.createDue()).isGreaterThanOrEqualTo(0);
        assertThat(status("futura")).isEqualTo("PLANNED");
        assertThat(status("tardia")).isEqualTo("PLANNED");
        assertThat(autoStartedRecommendations()).isEmpty();
    }

    @Test
    @Order(4)
    void onItsStartDayTheRoundActivatesItKeepsEverythingAndWarnsOnlyThatDay() throws Exception {
        Flow f = flow();
        JsonNode before = f.read("/api/promotions/futura");

        // Lunes 16 de nov, 9:30
        clock.set(ZonedDateTime.of(LocalDate.of(2026, 11, 16), java.time.LocalTime.of(9, 30), FlowClock.MADRID).toInstant());
        autoOrders.createDue();

        JsonNode after = f.read("/api/promotions/futura");
        assertThat(after.get("status").asString()).isEqualTo("ACTIVE");
        // Todo lo demás, igual que estaba
        for (String field : List.of("name", "description", "scope", "cadence", "starts_on", "ends_on", "metrics_source", "goals",
                "rules", "human_todos", "milestones", "resources", "automations", "routes", "settings", "brand_id", "demo")) {
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        }
        assertThat(after.get("milestones").get(0).get("label").asString()).isEqualTo("Días gratis");
        assertThat(after.get("automations").get(0).get("model").asString()).isEqualTo("SONNET");

        // Las demás, sin tocar
        assertThat(status("tardia")).isEqualTo("PLANNED");
        assertThat(status("acabada")).isEqualTo("PLANNED");
        assertThat(status("pausada")).isEqualTo("PAUSED");
        assertThat(status("activa")).isEqualTo("ACTIVE");

        // «Qué hacer ahora» lo cuenta ese día
        List<JsonNode> notices = autoStartedRecommendations();
        assertThat(notices).hasSize(1);
        assertThat(notices.getFirst().get("promotion_id").asString()).isEqualTo("futura");
        assertThat(notices.getFirst().get("title").asString()).isEqualTo("Promoción «Futura» activada sola: empieza hoy");

        // Y pasada la ronda otra vez no se repite nada: ni activación ni propuestas de la primera semana
        autoOrders.createDue();
        assertThat(autoStartedRecommendations()).hasSize(1);
        assertThat(proposalOrders("futura").stream().filter(o -> "2026-11-16".equals(o.get("inputs").get("items").get(0).asString())))
                .hasSize(1);

        // Al día siguiente el aviso ya no sale
        clock.set(ZonedDateTime.of(LocalDate.of(2026, 11, 17), java.time.LocalTime.of(9, 30), FlowClock.MADRID).toInstant());
        assertThat(autoStartedRecommendations()).isEmpty();
        assertThat(status("futura")).isEqualTo("ACTIVE");
    }

    @Test
    @Order(5)
    void aPromotionActivatedByHandHasNoAutomaticNotice() throws Exception {
        Flow f = flow();
        clock.set(ZonedDateTime.of(LocalDate.of(2026, 11, 30), java.time.LocalTime.of(9, 30), FlowClock.MADRID).toInstant());
        // «Tardia» empieza hoy; Raúl la activa a mano antes de la ronda
        JsonNode tardia = f.read("/api/promotions/tardia");
        f.ok(f.put("/api/promotions/tardia", promotion("Tardia", "2026-11-30", "2027-02-01", "ACTIVE")));
        autoOrders.createDue();
        assertThat(tardia.get("status").asString()).isEqualTo("PLANNED");
        assertThat(status("tardia")).isEqualTo("ACTIVE");
        assertThat(autoStartedRecommendations()).as("lo activó Raúl, no SocialHub").isEmpty();
    }

    // ------------------------------------------------------------------ utilidades

    private static String promotion(String name, String starts, String ends, String status) {
        return """
                {"brand_id":"el-delito","name":"%s","starts_on":"%s","ends_on":"%s","status":"%s"}""".formatted(name, starts, ends, status);
    }

    private String status(String promotionId) throws Exception {
        return flow().read("/api/promotions/" + promotionId).get("status").asString();
    }

    /** Los encargos de propuestas de esa promoción que ya han salido a «para-claude». */
    private List<JsonNode> proposalOrders(String promotionId) throws Exception {
        return flow().requestsOfType("para-claude", "socialhub.weekly-proposals").stream()
                .filter(o -> promotionId.equals(o.get("promotion").asString())).toList();
    }

    private List<JsonNode> autoStartedRecommendations() throws Exception {
        List<JsonNode> found = new java.util.ArrayList<>();
        flow().read("/api/factory").get("recommendations").forEach(r -> {
            if ("PROMOTION_AUTO_STARTED".equals(r.get("kind").asString())) found.add(r);
        });
        return found;
    }
}
