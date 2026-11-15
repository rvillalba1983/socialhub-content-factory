package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.flows.Flow;
import com.socialhub.flows.FlowClock;
import com.socialhub.flows.FlowTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * La ronda semanal W+1 empieza después de la ventana ampliada de la primera semana: una promoción que empieza el domingo
 * con cadencia x/v pide la primera semana del domingo 15 al miércoles 18 nov; la semana siguiente (lunes 16) se pide solo
 * desde el jueves 19, para que dos encargos no propongan los mismos días.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeeklyRoundAfterFirstWindowFlowTest {

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

    @Test
    @Order(1)
    void setup() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        // Domingo 15 nov, 10:00
        clock.set(ZonedDateTime.of(LocalDate.of(2026, 11, 15), LocalTime.of(10, 0), FlowClock.MADRID).toInstant());
    }

    @Test
    @Order(2)
    void theWeeklyRoundStartsAfterTheExtendedWindowOfTheFirstWeek() throws Exception {
        Flow f = flow();
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Primera","starts_on":"2026-11-15","ends_on":"2027-01-31","status":"ACTIVE",
                 "cadence":"x/v a las 19:00"}"""));
        autoOrders.createDue();
        f.waitFor(() -> proposalOrders().size() == 2, "las dos peticiones de propuestas enviadas");

        JsonNode first = byWeekFrom("2026-11-09");
        assertThat(first.get("inputs").get("week").get("to").asString()).isEqualTo("2026-11-18");
        // La semana del lunes 16 se pide solo desde el jueves 19
        JsonNode next = byWeekFrom("2026-11-19");
        assertThat(next.get("inputs").get("week").get("to").asString()).isEqualTo("2026-11-22");

        // Pedidas una sola vez, también en rondas posteriores
        autoOrders.createDue();
        assertThat(proposalOrders()).hasSize(2);

        // Un día de la ventana ya cubierta por la primera semana se descarta de la de W+1
        String task = next.get("taskId").asString();
        f.complete(task, """
                {"proposals":[
                  {"scheduled_on":"2026-11-18","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Post","title":"Ya cubierto","idea":"x","why":"y"},
                  {"scheduled_on":"2026-11-20","scheduled_time":"19:00","platform":"INSTAGRAM","type":"Post","title":"Viernes","idea":"x","why":"y"}],
                 "summary":"Dos"}""");
        assertThat(f.task(task).get("result").asString()).contains("1 fuera de la semana");
    }

    private List<JsonNode> proposalOrders() throws Exception {
        return flow().requestsOfType("para-claude", "socialhub.weekly-proposals").stream()
                .filter(o -> "primera".equals(o.get("promotion").asString())).toList();
    }

    private JsonNode byWeekFrom(String from) throws Exception {
        List<JsonNode> all = proposalOrders();
        return all.stream().filter(o -> from.equals(o.get("inputs").get("week").get("from").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("No hay una petición que empiece el " + from + ": " + all));
    }
}
