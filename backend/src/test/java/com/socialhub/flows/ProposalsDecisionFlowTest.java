package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Flujo «Aprobar y rechazar propuestas con motivo» (manual, §4), de extremo a extremo: el resultado de «Propuestas de la
 * semana» deja publicaciones en «Propuesta»; Raúl aprueba unas (pasan a «En producción») y rechaza otras con su motivo;
 * el motivo queda en la memoria editorial y vuelve en el siguiente encargo a Claude; y «Qué hacer ahora» cuenta lo que
 * queda por revisar.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProposalsDecisionFlowTest {

    @TempDir static Path drive;
    static Path orders;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    private static final String PROPOSALS = """
            {"proposals":[
              {"scheduled_on":"2026-11-16","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Carrusel","title":"Tres versos de la herida",
               "idea":"Carrusel con tres versos","why":"Funciona el formato"},
              {"scheduled_on":"2026-11-18","scheduled_time":"13:00","platform":"INSTAGRAM","type":"Historia","title":"Pregunta grande"},
              {"scheduled_on":"2026-11-20","scheduled_time":"19:00","platform":"tiktok","type":"Reel","title":"Leer en voz alta"},
              {"scheduled_on":"2026-11-30","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Carrusel","title":"De otra semana"}],
             "summary":"Semana tranquila"}""";

    @Test
    @Order(1)
    void theProposalsArriveAsPublicationsToReview() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        // Algo que ya estaba planificado esa semana: Claude lo recibe para no repetirlo
        f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Reel","title":"Ya planificado","scheduled_on":"2026-11-17","scheduled_time":"19:00",
                 "initial_status":"IN_PRODUCTION"}"""));

        String task = f.order("poemario", "WEEKLY_PROPOSALS", "Propón la semana", "2026-11-16");
        JsonNode request = f.request(task);
        assertThat(request.get("inputs").get("week").get("from").asString()).isEqualTo("2026-11-16");
        assertThat(request.get("inputs").get("week").get("to").asString()).isEqualTo("2026-11-22");
        assertThat(request.get("inputs").get("already_planned").get(0).get("title").asString()).isEqualTo("Ya planificado");

        f.complete(task, PROPOSALS);
        assertThat(f.task(task).get("result").asString())
                .isEqualTo("3 propuestas para revisar en Publicaciones · 1 descartada: 1 fuera de la semana · Semana tranquila");
        assertThat(f.status("poemario", "2026-11-16-instagram")).isEqualTo("PROPOSAL");
        assertThat(f.status("poemario", "2026-11-18-instagram")).isEqualTo("PROPOSAL");
        assertThat(f.status("poemario", "2026-11-20-tiktok")).isEqualTo("PROPOSAL");
        assertThat(f.publication("poemario", "2026-11-16-instagram").get("note").asString())
                .isEqualTo("Idea: Carrusel con tres versos · Por qué: Funciona el formato");
        assertThat(f.read("/api/promotions/poemario/publications?status=PROPOSAL").get("total").asInt()).isEqualTo(3);

        assertThat(toReview()).isEqualTo("Tienes 3 propuestas de «Poemario» para revisar");
    }

    @Test
    @Order(2)
    void approvingMovesItToProductionAndTheOthersWaitForTheirDecision() throws Exception {
        Flow f = flow();
        f.post("/api/publications/2026-11-16-instagram/status-changes", "{\"status\":\"IN_PRODUCTION\"}").andExpect(status().isOk());
        assertThat(f.status("poemario", "2026-11-16-instagram")).isEqualTo("IN_PRODUCTION");
        assertThat(toReview()).isEqualTo("Tienes 2 propuestas de «Poemario» para revisar");

        // Una propuesta no salta etapas: de «Propuesta» no se pasa directamente a «Aprobada» ni a «Programada»
        f.post("/api/publications/2026-11-18-instagram/status-changes", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict());
        f.post("/api/publications/2026-11-18-instagram/status-changes", "{\"status\":\"SCHEDULED\"}").andExpect(status().isConflict());
        assertThat(f.status("poemario", "2026-11-18-instagram")).isEqualTo("PROPOSAL");

        // La aprobada ya entra en el spec de piezas; las que esperan decisión, no
        String spec = f.order("poemario", "WEEKLY_SPEC", "spec", "2026-11-16-instagram");
        JsonNode planned = f.request(spec).get("inputs").get("publications");
        assertThat(planned.size()).isEqualTo(1);
        assertThat(planned.get(0).get("id").asString()).isEqualTo("2026-11-16-instagram");
    }

    @Test
    @Order(3)
    void rejectingNeedsAValidReasonAndKeepsItInTheEditorialMemory() throws Exception {
        Flow f = flow();
        // Sin motivo, con un motivo que no existe o sobre algo que no existe: no cambia nada
        f.post("/api/publications/2026-11-18-instagram/status-changes", "{\"status\":\"CANCELLED\"}").andExpect(status().isBadRequest());
        f.post("/api/publications/2026-11-18-instagram/status-changes", "{\"status\":\"CANCELLED\",\"reason\":\"NO_ME_GUSTA\"}")
                .andExpect(status().isBadRequest());
        f.post("/api/publications/no-existe/status-changes", "{\"status\":\"CANCELLED\",\"reason\":\"TONE\"}").andExpect(status().isNotFound());
        assertThat(f.status("poemario", "2026-11-18-instagram")).isEqualTo("PROPOSAL");
        assertThat(f.read("/api/brands/el-delito/editorial-memory").get("rejections").size()).isZero();

        // Con motivo y nota
        JsonNode rejected = f.ok(f.post("/api/publications/2026-11-18-instagram/status-changes",
                "{\"status\":\"CANCELLED\",\"reason\":\"TONE\",\"note\":\"  Demasiado solemne para una historia  \"}"));
        assertThat(rejected.get("status").asString()).isEqualTo("CANCELLED");
        // Y otra con motivo y sin nota
        f.ok(f.post("/api/publications/2026-11-20-tiktok/status-changes", "{\"status\":\"CANCELLED\",\"reason\":\"REPEATED\"}"));

        JsonNode memory = f.read("/api/brands/el-delito/editorial-memory");
        assertThat(memory.get("rejections").size()).isEqualTo(2);
        JsonNode first = memory.get("rejections").get(0);
        assertThat(List.of(first.get("title").asString(), memory.get("rejections").get(1).get("title").asString()))
                .containsExactlyInAnyOrder("Pregunta grande", "Leer en voz alta");
        for (JsonNode r : memory.get("rejections")) {
            assertThat(r.get("kind").asString()).isEqualTo("PUBLICATION");
            if (r.get("title").asString().equals("Pregunta grande")) {
                assertThat(r.get("reason").asString()).isEqualTo("TONE");
                assertThat(r.get("note").asString()).isEqualTo("Demasiado solemne para una historia");
            } else {
                assertThat(r.get("reason").asString()).isEqualTo("REPEATED");
                assertThat(r.has("note") && !r.get("note").isNull()).isFalse();
            }
        }
        // Ya no quedan propuestas por revisar
        assertThat(toReview()).isNull();
        assertThat(f.read("/api/promotions/poemario/publications?status=PROPOSAL").get("total").asInt()).isZero();
        assertThat(f.read("/api/promotions/poemario/publications?status=CANCELLED").get("total").asInt()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void theNextProposalsOrderCarriesWhatWasRejectedAndWhy() throws Exception {
        Flow f = flow();
        String task = f.order("poemario", "WEEKLY_PROPOSALS", "Propón la semana siguiente", "2026-11-23");
        JsonNode memory = f.request(task).get("inputs").get("editorial_memory");
        List<String> what = new ArrayList<>();
        memory.get("rejections").forEach(r -> what.add(r.get("title").asString() + " | " + r.get("reason").asString()
                + " | " + r.get("what").asString() + (r.has("note") ? " | " + r.get("note").asString() : "")));
        assertThat(what).containsExactlyInAnyOrder(
                "Pregunta grande | Tono | publicación cancelada | Demasiado solemne para una historia",
                "Leer en voz alta | Repetido | publicación cancelada");
        // Lo aprobado (todavía en producción) no cuenta como publicado
        assertThat(memory.get("published_last_8_weeks").size()).isZero();

        // Una propuesta rechazada se puede recuperar: vuelve a idea y de ahí a propuesta
        f.post("/api/publications/2026-11-20-tiktok/status-changes", "{\"status\":\"IDEA\"}").andExpect(status().isOk());
        f.post("/api/publications/2026-11-20-tiktok/status-changes", "{\"status\":\"PROPOSAL\"}").andExpect(status().isOk());
        assertThat(toReview()).isEqualTo("Tienes 1 propuesta de «Poemario» para revisar");
        // El rechazo anterior sigue en la memoria: lo que se aprendió no se borra
        assertThat(f.read("/api/brands/el-delito/editorial-memory").get("rejections").size()).isEqualTo(2);
        assertThat(Files.isDirectory(orders.resolve("para-claude"))).isTrue();
    }

    /** El texto de «Tienes N propuestas… para revisar» de «Qué hacer ahora», o nulo si no sale. */
    private String toReview() throws Exception {
        for (JsonNode r : flow().read("/api/factory").get("recommendations")) {
            if ("PROPOSALS_TO_REVIEW".equals(r.get("kind").asString())) return r.get("title").asString();
        }
        return null;
    }
}
