package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Autonomía (fase 8, bloque 2): textos de lo que está en producción sin texto y revisión al pasar a «Pendiente de revisión». */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.initial-delay-millis=3600000",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
class AutoRequestsTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final LocalDate DAY = LocalDate.now(MADRID).plusDays(10);

    @TempDir static Path drive;

    @Autowired MockMvc mvc;
    @Autowired AutoRequests auto;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void textsAndReviewsAreRequestedOnce() throws Exception {
        Path orders = Files.createDirectories(drive.resolve("encargos"));
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}");
        write(post("/api/promotions"), """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(LocalDate.now(MADRID), DAY.plusDays(30))).andExpect(status().isCreated());
        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + q(orders) + "}]}")
                .andExpect(status().isOk());
        String reel = create("Reel", "19:00", "IN_PRODUCTION");
        String carousel = create("Carrusel", "20:00", "DRAFT");
        create("Historia", "21:00", "IN_PRODUCTION");     // las historias no llevan texto
        create("Reel", "22:00", "IDEA");                  // aún no está en producción

        assertThat(auto.requestDue()).isEqualTo(1);
        List<JsonNode> captions = tasks("AI_CAPTIONS");
        assertThat(captions).hasSize(1);
        assertThat(captions.getFirst().get("inputs").toString()).contains(reel).contains(carousel).doesNotContain("21:00");
        // No se repite aunque sigan sin texto
        assertThat(auto.requestDue()).isZero();

        // Al pasar a «Pendiente de revisión» con texto, se pide la revisión una vez por versión del texto
        write(put("/api/publications/" + reel), """
                {"platform":"INSTAGRAM","type":"Reel","title":"Reel 19:00","scheduled_on":"%s","scheduled_time":"19:00",
                 "caption":"Un verso cada día"}""".formatted(DAY)).andExpect(status().isOk());
        assertThat(auto.requestDue()).isZero();
        for (String s : List.of("DRAFT", "IN_REVIEW")) {
            write(post("/api/publications/" + reel + "/status-changes"), "{\"status\":\"" + s + "\"}").andExpect(status().isOk());
        }
        assertThat(auto.requestDue()).isEqualTo(1);
        assertThat(tasks("AI_QUALITY_REVIEW")).hasSize(1);
        assertThat(auto.requestDue()).isZero();

        write(post("/api/publications/" + reel + "/status-changes"), "{\"status\":\"DRAFT\"}").andExpect(status().isOk());
        write(put("/api/publications/" + reel), """
                {"platform":"INSTAGRAM","type":"Reel","title":"Reel 19:00","scheduled_on":"%s","scheduled_time":"19:00",
                 "caption":"Un verso cada mañana"}""".formatted(DAY)).andExpect(status().isOk());
        write(post("/api/publications/" + reel + "/status-changes"), "{\"status\":\"IN_REVIEW\"}").andExpect(status().isOk());
        assertThat(auto.requestDue()).isEqualTo(1);
        assertThat(tasks("AI_QUALITY_REVIEW")).hasSize(2);
    }

    private String create(String type, String time, String status) throws Exception {
        String body = write(post("/api/promotions/poemario/publications"), """
                {"platform":"INSTAGRAM","type":"%s","title":"%s %s","scheduled_on":"%s","scheduled_time":"%s","initial_status":"%s"}"""
                .formatted(type, type, time, DAY, time, status)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    private List<JsonNode> tasks(String type) throws Exception {
        JsonNode factory = json.readTree(mvc.perform(get("/api/factory")).andReturn().getResponse().getContentAsString());
        List<JsonNode> list = new ArrayList<>();
        factory.get("tasks").forEach(t -> { if (t.get("type").asString().equals(type)) list.add(t); });
        return list;
    }

    private String q(Object text) throws Exception { return json.writeValueAsString(text.toString()); }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
