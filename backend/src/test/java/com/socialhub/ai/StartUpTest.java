package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Puesta en marcha de una marca y una promoción nuevas: las carpetas se crean desde una carpeta base, la promoción pide
 * sus primeras propuestas y, mientras falta la carpeta de «Encargos», el encargo sale en «Necesita atención».
 */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.ai.initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
class StartUpTest {

    @TempDir Path drive;
    @Autowired MockMvc mvc;
    @Autowired AiAutoOrders auto;
    @Autowired AiDispatcher dispatcher;

    @Test
    void foldersFirstProposalsAndHeldOrders() throws Exception {
        write(post("/api/brands"), "{\"name\":\"Litoques\",\"handle\":\"@litoques\",\"color\":\"#C98A1B\"}")
                .andExpect(status().isCreated());
        LocalDate today = LocalDate.now();
        write(post("/api/promotions"), """
                {"brand_id":"litoques","name":"Test inicial","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(today, today.plusMonths(2))).andExpect(status().isCreated());

        // La promoción pide enseguida las propuestas de su primera semana (y solo una vez)
        assertThat(auto.createFirstWeeks(java.time.ZonedDateTime.now())).isEqualTo(1);
        assertThat(auto.createFirstWeeks(java.time.ZonedDateTime.now())).isZero();

        // Sin carpeta de «Encargos» el encargo no sale y Raúl lo ve en «Necesita atención»
        dispatcher.dispatchQueued();
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.alerts[?(@.title =~ /En cola: Propuestas de la primera semana.*/)].detail")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("carpeta de «Encargos»"))));

        // Sin carpeta base ni rutas no hay nada que crear
        write(post("/api/brands/litoques/folders/creation"), "{}").andExpect(status().isConflict());

        // Con una carpeta base se crean las siete y se asignan
        Path base = drive.resolve("SocialHub").resolve("Litoques");
        write(post("/api/brands/litoques/folders/creation"), "{\"base\":\"" + base.toString().replace("\\", "\\\\") + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[?(@.role == 'ORDERS')].location").value(base.resolve("Encargos").toString()))
                .andExpect(jsonPath("$.items[?(@.available == false)]").isEmpty());
        for (String name : new String[] {"Material", "Borradores", "Generados", "Aprobados", "Publicados", "Informes", "Encargos"}) {
            assertThat(base.resolve(name)).isDirectory();
        }

        // Ya tiene carpeta: el encargo sale y deja de salir en «Necesita atención»
        assertThat(dispatcher.dispatchQueued()).isEqualTo(1);
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.alerts[?(@.title =~ /En cola:.*/)]").isEmpty());

        // Si luego falta una carpeta asignada, el botón la vuelve a crear sin tocar las demás
        Files.delete(base.resolve("Informes"));
        write(post("/api/brands/litoques/folders/creation"), "{}").andExpect(status().isOk());
        assertThat(base.resolve("Informes")).isDirectory();
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
