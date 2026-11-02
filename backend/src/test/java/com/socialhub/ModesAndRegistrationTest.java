package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppPaths;
import java.nio.file.Files;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Modo real y modo demo con bases de datos separadas, y alta de marcas y promociones. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModesAndRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppPaths paths;

    @Test
    @Order(1)
    void startsInRealModeWithOnlyTheStations() throws Exception {
        mvc.perform(get("/api/app-mode"))
                .andExpect(jsonPath("$.mode").value("REAL"))
                .andExpect(jsonPath("$.demo_available").value(true));
        mvc.perform(get("/api/factory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.stations.length()").value(8))
                .andExpect(jsonPath("$.stations[0].status").value("IDLE"))
                .andExpect(jsonPath("$.brands.length()").value(0))
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.publications.length()").value(0));
    }

    @Test
    @Order(2)
    void brandsAndPromotionsAreRegisteredFromTheApp() throws Exception {
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("el-delito"))
                .andExpect(jsonPath("$.demo").value(false));
        write(post("/api/brands"), "{\"name\":\"El DeLito\",\"handle\":\"@otro\",\"color\":\"#000000\"}")
                .andExpect(jsonPath("$.id").value("el-delito-2"));
        write(post("/api/brands"), "{\"name\":\"Mal\",\"handle\":\"@mal\",\"color\":\"rojo\"}")
                .andExpect(status().isBadRequest());
        write(put("/api/brands/el-delito-2"), "{\"name\":\"Litoques\",\"handle\":\"@litoques\",\"color\":\"#C98A1B\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Litoques"));

        String promotion = """
                {"brand_id":"el-delito","name":"Lanzamiento del poemario","starts_on":"2026-11-03","ends_on":"2027-01-25",
                 "status":"ACTIVE","goals":["Lecturas en Kindle Unlimited"],
                 "milestones":[{"starts_on":"2026-11-21","ends_on":"2026-11-23","label":"Días gratis"}],
                 "resources":[{"kind":"DRIVE","location":"G:\\\\Mi unidad\\\\DeLito","description":"Material"}],
                 "automations":[{"name":"Tanda semanal","schedule":"Lunes 9:00","model":"SONNET","runs_on":"CLOUD","status":"OK"}],
                 "settings":[{"label":"Hora","value":"19:00","origin":"BRAND"}]}""";
        write(post("/api/promotions"), promotion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("lanzamiento-del-poemario"))
                .andExpect(jsonPath("$.milestones[0].label").value("Días gratis"))
                .andExpect(jsonPath("$.automations[0].model").value("SONNET"))
                .andExpect(jsonPath("$.description").value(""));
        write(put("/api/promotions/lanzamiento-del-poemario"), promotion.replace("ACTIVE", "PAUSED").replace("\"Días gratis\"", "\"Días gratis (KDP)\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.milestones.length()").value(1))
                .andExpect(jsonPath("$.milestones[0].label").value("Días gratis (KDP)"));
        write(post("/api/promotions"), promotion.replace("el-delito", "no-existe")).andExpect(status().isBadRequest());
        write(post("/api/promotions"), promotion.replace("2027-01-25", "2026-10-01")).andExpect(status().isBadRequest());
        write(put("/api/promotions/no-existe"), promotion).andExpect(status().isNotFound());
        mvc.perform(post("/api/brands").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void demoModeHasItsOwnDatabaseAndTheChoiceIsRemembered() throws Exception {
        write(put("/api/app-mode"), "{\"mode\":\"DEMO\"}").andExpect(jsonPath("$.mode").value("DEMO"));
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.tasks.length()").value(24))
                .andExpect(jsonPath("$.brands[0].id").value("delito"));
        assertThat(Files.readString(paths.dataDir().resolve("mode"))).isEqualTo(AppMode.DEMO.name());

        write(put("/api/app-mode"), "{\"mode\":\"REAL\"}").andExpect(jsonPath("$.mode").value("REAL"));
        mvc.perform(get("/api/factory"))
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.brands.length()").value(2))
                .andExpect(jsonPath("$.promotions[0].id").value("lanzamiento-del-poemario"));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
