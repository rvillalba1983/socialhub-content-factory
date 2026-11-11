package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.publishing.domain.MetricsRepository;
import com.socialhub.publishing.domain.MetricsRepository.Metrics;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GET /api/promotions/{id}/metrics: totales por red y medias por formato, solo con lo medido. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
class PromotionMetricsApiTest {

    @Autowired MockMvc mvc;
    @Autowired PublicationRepository publications;
    @Autowired MetricsRepository metrics;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void aggregatesTheMeasuredPublicationsOfThePromotion() throws Exception {
        mvc.perform(post("/api/brands").header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}")).andExpect(status().isCreated());
        for (String name : new String[] {"Vacía", "Medida"}) {
            mvc.perform(post("/api/promotions").header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"brand_id\":\"el-delito\",\"name\":\"" + name + "\",\"starts_on\":\"2026-11-01\","
                            + "\"ends_on\":\"2027-01-31\",\"status\":\"ACTIVE\"}")).andExpect(status().isCreated());
        }

        // Una promoción sin métricas: todo vacío; y 404 si no existe
        JsonNode empty = body("/api/promotions/vacia/metrics");
        assertThat(empty.get("measured").asInt()).isZero();
        assertThat(empty.get("pending").asInt()).isZero();
        assertThat(empty.get("by_format")).isEmpty();
        assertThat(empty.get("by_network")).hasSize(3);
        assertThat(empty.get("by_network").get(2).get("network").asString()).isEqualTo("TOTAL");
        assertThat(present(empty.get("by_network").get(0), "views")).isFalse();
        mvc.perform(get("/api/promotions/no-existe/metrics")).andExpect(status().isNotFound());

        // 5 reels de Instagram medidos (grupo con muestra), 1 carrusel (pocos datos), 1 vídeo de TikTok con cifras null
        // y 1 publicada sin medir aún
        for (int i = 1; i <= 5; i++) measured("r" + i, "medida", Platform.INSTAGRAM, "Reel", i * 100L, 50L, 10L);
        measured("c1", "medida", Platform.INSTAGRAM, "Carrusel", null, 70L, null);
        measured("t1", "medida", Platform.TIKTOK, "Vídeo", 900L, null, null);
        publications.save(publication("p1", "medida", Platform.TIKTOK, "Vídeo", PublicationStatus.PUBLISHED));
        // Una medida de otra promoción no se mezcla
        measured("x1", "vacia", Platform.INSTAGRAM, "Reel", 7L, 7L, 7L);

        JsonNode r = body("/api/promotions/medida/metrics");
        assertThat(r.get("measured").asInt()).isEqualTo(7);
        assertThat(r.get("pending").asInt()).isEqualTo(1);

        JsonNode ig = r.get("by_network").get(0);
        assertThat(ig.get("network").asString()).isEqualTo("INSTAGRAM");
        assertThat(ig.get("publications").asInt()).isEqualTo(6);
        assertThat(ig.get("views").asLong()).isEqualTo(1500);
        assertThat(ig.get("reach").asLong()).isEqualTo(320);
        JsonNode tt = r.get("by_network").get(1);
        assertThat(tt.get("network").asString()).isEqualTo("TIKTOK");
        assertThat(tt.get("views").asLong()).isEqualTo(900);
        assertThat(present(tt, "reach")).isFalse(); // sin dato, no cero
        JsonNode total = r.get("by_network").get(2);
        assertThat(total.get("network").asString()).isEqualTo("TOTAL");
        assertThat(total.get("publications").asInt()).isEqualTo(7);
        assertThat(total.get("views").asLong()).isEqualTo(2400);

        assertThat(r.get("by_format")).hasSize(3);
        JsonNode carousel = r.get("by_format").get(0);
        assertThat(carousel.get("type").asString()).isEqualTo("Carrusel");
        assertThat(carousel.get("low_sample").asBoolean()).isTrue();
        JsonNode reels = r.get("by_format").get(1);
        assertThat(reels.get("type").asString()).isEqualTo("Reel");
        assertThat(reels.get("network").asString()).isEqualTo("INSTAGRAM");
        assertThat(reels.get("publications").asInt()).isEqualTo(5);
        assertThat(reels.get("low_sample").asBoolean()).isFalse();
        assertThat(reels.get("views").asDouble()).isEqualTo(300.0);
        assertThat(reels.get("reach").asDouble()).isEqualTo(50.0);
        assertThat(r.get("by_format").get(2).get("low_sample").asBoolean()).isTrue();
    }

    private static boolean present(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
    }

    private JsonNode body(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static Publication publication(String id, String promotion, Platform platform, String type, PublicationStatus status) {
        return Publication.builder().id(id).promotionId(promotion).platform(platform).type(type).title(id)
                .scheduledOn(LocalDate.of(2026, 11, 2)).scheduledTime(LocalTime.of(19, 0)).status(status)
                .confirmation(status == PublicationStatus.PUBLISHED ? "ok" : null)
                .publishedAt(status == PublicationStatus.PUBLISHED ? Instant.parse("2026-11-02T18:00:00Z") : null).build();
    }

    private void measured(String id, String promotion, Platform platform, String type, @Nullable Long views,
                          @Nullable Long reach, @Nullable Long likes) {
        publications.save(publication(id, promotion, platform, type, PublicationStatus.PUBLISHED));
        metrics.save(new Metrics(id, MetricsRepository.Status.DONE, null, 1, Instant.parse("2026-11-02T18:00:00Z"),
                Instant.parse("2026-11-09T18:00:00Z"), views, reach, likes, null, null, null, null));
    }
}
