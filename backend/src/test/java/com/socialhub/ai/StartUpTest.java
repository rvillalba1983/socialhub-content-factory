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
@org.springframework.context.annotation.Import(com.socialhub.PinnedClock.class)
@org.springframework.test.context.event.RecordApplicationEvents
class StartUpTest {

    @TempDir Path drive;
    @Autowired MockMvc mvc;
    @Autowired AiAutoOrders auto;
    @Autowired java.time.Clock clock;
    @Autowired AiDispatcher dispatcher;
    @Autowired AiSentOrders sentOrders;
    @Autowired AiRequestRepository requests;
    @Autowired com.socialhub.publishing.PublishingQueries publishing;
    @Autowired org.springframework.test.context.event.ApplicationEvents events;

    @Test
    void foldersFirstProposalsAndHeldOrders() throws Exception {
        write(post("/api/brands"), "{\"name\":\"Litoques\",\"handle\":\"@litoques\",\"color\":\"#C98A1B\"}")
                .andExpect(status().isCreated());
        LocalDate today = LocalDate.now(clock);
        // El domingo por la tarde ya no queda hueco válido esa semana: empieza el lunes, que sí lo tiene
        LocalDate start = today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? today.plusDays(1) : today;
        // Al guardar, la promoción pide enseguida las propuestas de su primera semana y el guardado lo avisa
        String saved = write(post("/api/promotions"), """
                {"brand_id":"litoques","name":"Test inicial","starts_on":"%s","ends_on":"%s","status":"ACTIVE"}"""
                .formatted(start, today.plusMonths(2))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(saved).contains("proposals_note").contains("Propuestas de la primera semana pedidas");

        // Solo se pide una vez: la ronda posterior no crea otro
        assertThat(auto.createFirstWeeks(java.time.ZonedDateTime.now(clock))).isZero();
        assertThat(auto.createFirstWeeks(java.time.ZonedDateTime.now(clock))).isZero();

        // Sin carpeta de «Encargos» el encargo no sale y Raúl lo ve en «Qué hacer ahora» (no en «Necesita atención»)
        events.clear();
        dispatcher.dispatchQueued();
        assertThat(aiChanges()).isEqualTo(1);
        // Misma situación en la ronda siguiente: la pantalla no se refresca sin motivo
        events.clear();
        dispatcher.dispatchQueued();
        assertThat(aiChanges()).isZero();
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.recommendations[?(@.kind == 'HELD_PROPOSALS')].detail")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("carpeta de «Encargos»"))))
                .andExpect(jsonPath("$.alerts[?(@.title =~ /En cola:.*/)]").isEmpty());

        // Sin carpeta base ni rutas no hay nada que crear
        write(post("/api/brands/litoques/folders/creation"), "{}").andExpect(status().isConflict());

        // Con una carpeta base se crean las siete y se asignan
        Path base = drive.resolve("SocialHub").resolve("Litoques");
        write(post("/api/brands/litoques/folders/creation"), "{\"base\":\"" + base.toString().replace("\\", "\\\\") + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[?(@.role == 'ORDERS')].location").value(base.resolve("encargos").toString()))
                .andExpect(jsonPath("$.items[?(@.available == false)]").isEmpty());
        for (String name : new String[] {"borradores", "generados", "aprobados", "publicados", "informes", "encargos"}) {
            assertThat(base.resolve(name)).isDirectory();
        }

        // Ya tiene carpeta: el encargo sale y deja de salir en «Necesita atención»
        events.clear();
        assertThat(dispatcher.dispatchQueued()).isEqualTo(1);
        // Al dejar de estar retenido, la pantalla se refresca
        assertThat(aiChanges()).isEqualTo(1);
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.alerts[?(@.title =~ /En cola:.*/)]").isEmpty())
                .andExpect(jsonPath("$.recommendations[?(@.kind == 'HELD_PROPOSALS')]").isEmpty())
                .andExpect(jsonPath("$.recommendations[?(@.kind == 'PROPOSALS_PENDING')]").isNotEmpty());

        // Con base de datos: el encargo enviado cuenta mientras su tarea espera a Claude y deja de contar si se cancela
        var waiting = sentOrders.waiting();
        assertThat(waiting).hasSize(1);
        assertThat(waiting.getFirst().type()).isEqualTo(AiRequestType.WEEKLY_PROPOSALS);
        assertThat(waiting.getFirst().ordersAvailable()).isTrue();
        String taskId = requests.findByStatus(AiRequestEntity.Status.SENT).getFirst().getTaskId();
        write(post("/api/tasks/" + taskId + "/commands"), "{\"type\":\"CANCEL\"}").andExpect(status().isOk());
        assertThat(requests.findByStatus(AiRequestEntity.Status.SENT)).hasSize(1);
        assertThat(sentOrders.waiting()).isEmpty();
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.recommendations[?(@.kind == 'PROPOSALS_PENDING')]").isEmpty());

        // Sin propuestas por revisar no hay contadores
        assertThat(publishing.proposalCounts()).isEmpty();

        // Si luego falta una carpeta asignada, el botón la vuelve a crear sin tocar las demás
        Files.delete(base.resolve("informes"));
        write(post("/api/brands/litoques/folders/creation"), "{}").andExpect(status().isOk());
        assertThat(base.resolve("informes")).isDirectory();
    }

    private long aiChanges() {
        return events.stream(com.socialhub.platform.live.FactoryChanged.class).filter(e -> "ai".equals(e.kind())).count();
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
