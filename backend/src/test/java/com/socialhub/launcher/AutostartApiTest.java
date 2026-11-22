package com.socialhub.launcher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.flows.FlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Fuera de SocialHub instalado (pruebas, IDE, Linux) el arranque con el sistema no está disponible y no toca nada. */
@FlowTest
class AutostartApiTest {

    @Autowired MockMvc mvc;

    @Test
    void outsideTheInstalledProgramItIsUnavailableAndCannotBeChanged() throws Exception {
        mvc.perform(get("/api/system/autostart")).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/api/system/autostart").header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")).andExpect(status().isBadRequest());
    }
}
