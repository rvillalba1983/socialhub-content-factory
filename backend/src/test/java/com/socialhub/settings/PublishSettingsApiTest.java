package com.socialhub.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** El «Margen mínimo de publicación»: por defecto 30 minutos, entre 5 y 1440, guardado de verdad y leído por el puerto. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=DEMO"
})
@AutoConfigureMockMvc
class PublishSettingsApiTest {

    @Autowired MockMvc mvc;
    @Autowired PublishSettings settings;

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/settings/publishing").header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void theMarginDefaultsToThirtyMinutesAndIsAdjustableWithinLimits() throws Exception {
        mvc.perform(get("/api/settings/publishing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.min_margin_minutes").value(30))
                .andExpect(jsonPath("$.min_minutes").value(5))
                .andExpect(jsonPath("$.max_minutes").value(1440));
        assertThat(settings.minMargin()).isEqualTo(Duration.ofMinutes(30));

        save("{\"min_margin_minutes\":90}").andExpect(status().isOk()).andExpect(jsonPath("$.min_margin_minutes").value(90));
        assertThat(settings.minMargin()).isEqualTo(Duration.ofMinutes(90));
        mvc.perform(get("/api/settings/publishing")).andExpect(jsonPath("$.min_margin_minutes").value(90));

        // Nunca al instante: por debajo de 5 minutos o por encima de un día se rechaza y no cambia nada
        save("{\"min_margin_minutes\":4}").andExpect(status().isBadRequest());
        save("{\"min_margin_minutes\":0}").andExpect(status().isBadRequest());
        save("{\"min_margin_minutes\":1441}").andExpect(status().isBadRequest());
        assertThat(settings.minMargin()).isEqualTo(Duration.ofMinutes(90));

        save("{\"min_margin_minutes\":5}").andExpect(status().isOk());
        save("{\"min_margin_minutes\":1440}").andExpect(status().isOk());
        save("{\"min_margin_minutes\":30}").andExpect(status().isOk());
    }

    @Test
    void tikTokHasItsOwnMarginWithEightHoursByDefault() throws Exception {
        mvc.perform(get("/api/settings/publishing"))
                .andExpect(jsonPath("$.tiktok_min_margin_minutes").value(480))
                .andExpect(jsonPath("$.tiktok_default_minutes").value(480));
        assertThat(settings.minMarginFor("TIKTOK")).isEqualTo(Duration.ofHours(8));

        // Retrocompatible: sin el campo de TikTok solo cambia el general
        save("{\"min_margin_minutes\":45}").andExpect(status().isOk())
                .andExpect(jsonPath("$.min_margin_minutes").value(45)).andExpect(jsonPath("$.tiktok_min_margin_minutes").value(480));
        save("{\"min_margin_minutes\":45,\"tiktok_min_margin_minutes\":720}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tiktok_min_margin_minutes").value(720));
        assertThat(settings.minMarginFor("TIKTOK")).isEqualTo(Duration.ofHours(12));
        assertThat(settings.minMarginFor("INSTAGRAM")).isEqualTo(Duration.ofMinutes(45));
        mvc.perform(get("/api/settings/publishing")).andExpect(jsonPath("$.tiktok_min_margin_minutes").value(720));

        // Límites de 5 a 1440 también para TikTok, y un rechazo no cambia ninguno de los dos
        save("{\"min_margin_minutes\":60,\"tiktok_min_margin_minutes\":4}").andExpect(status().isBadRequest());
        save("{\"min_margin_minutes\":60,\"tiktok_min_margin_minutes\":1441}").andExpect(status().isBadRequest());
        assertThat(settings.minMarginFor("TIKTOK")).isEqualTo(Duration.ofHours(12));
        assertThat(settings.minMarginFor("INSTAGRAM")).isEqualTo(Duration.ofMinutes(45));
        save("{\"min_margin_minutes\":5,\"tiktok_min_margin_minutes\":5}").andExpect(status().isOk());
        save("{\"min_margin_minutes\":1440,\"tiktok_min_margin_minutes\":1440}").andExpect(status().isOk());
        save("{\"min_margin_minutes\":30,\"tiktok_min_margin_minutes\":480}").andExpect(status().isOk());
    }

    @Test
    void changesNeedTheGuardHeader() throws Exception {
        mvc.perform(put("/api/settings/publishing").contentType(MediaType.APPLICATION_JSON).content("{\"min_margin_minutes\":60}"))
                .andExpect(status().isForbidden());
    }
}
