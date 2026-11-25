package com.socialhub.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** El ajuste «Carpeta de audio de Litoques»: se guarda de verdad, avisa si no existe y rechaza rutas que no son completas. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=DEMO"
})
@AutoConfigureMockMvc
class AudioSettingsApiTest {

    @Autowired MockMvc mvc;
    @Autowired AudioSettings settings;
    @TempDir Path folder;

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/settings/audio").header("X-SocialHub-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void theFolderIsSavedReadAndRemoved() throws Exception {
        mvc.perform(get("/api/settings/audio")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        assertThat(settings.litoquesFolder()).isEmpty();

        String path = folder.toString().replace("\\", "\\\\");
        save("{\"litoques_folder\":\"" + path + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.litoques_folder").value(folder.toString())).andExpect(jsonPath("$.available").value(true));
        assertThat(settings.litoquesFolder()).contains(folder);

        // Una carpeta que no existe ahora (Drive sin montar) se guarda, pero se avisa
        Path missing = folder.resolve("no-esta");
        assertThat(Files.exists(missing)).isFalse();
        save("{\"litoques_folder\":\"" + missing.toString().replace("\\", "\\\\") + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        save("{\"litoques_folder\":\"\"}").andExpect(status().isOk());
        assertThat(settings.litoquesFolder()).isEmpty();
    }

    @Test
    void aRelativePathIsRejectedInSpanish() throws Exception {
        save("{\"litoques_folder\":\"audio/litoques\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ruta completa")));
    }
}
