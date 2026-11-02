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
                // Solo la comprobación de herramientas locales que se pone en cola al arrancar
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].id").value("check-local-tools"))
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
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.brands.length()").value(2))
                .andExpect(jsonPath("$.promotions[0].id").value("lanzamiento-del-poemario"));
    }

    @Test
    @Order(4)
    void folderRolesComeFromTheBrandWithPromotionExceptions() throws Exception {
        String material = Files.createDirectories(paths.dataDir().resolve("drive/material")).toAbsolutePath().toString();
        String orders = paths.dataDir().resolve("drive/encargos").toAbsolutePath().toString();
        String own = Files.createDirectories(paths.dataDir().resolve("drive/encargos-poemario")).toAbsolutePath().toString();

        write(put("/api/brands/el-delito/folders"), """
                {"items":[{"role":"MATERIAL","location":"%s"},{"role":"ORDERS","location":"%s"}]}"""
                .formatted(json(material), json(orders)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("MATERIAL"))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[1].available").value(false));

        mvc.perform(get("/api/promotions/lanzamiento-del-poemario/folders"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].role").value("ORDERS"))
                .andExpect(jsonPath("$.items[1].origin").value("BRAND"));

        write(put("/api/promotions/lanzamiento-del-poemario/folders"), """
                {"items":[{"role":"ORDERS","location":"  %s  "}]}""".formatted(json(own)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].origin").value("BRAND"))
                .andExpect(jsonPath("$.items[1].location").value(own))
                .andExpect(jsonPath("$.items[1].origin").value("PROMOTION"))
                .andExpect(jsonPath("$.items[1].available").value(true));

        // La carpeta de encargos de la promoción es la que vigila SocialHub
        mvc.perform(get("/api/promotions/lanzamiento-del-poemario/piece-specs"))
                .andExpect(jsonPath("$.folder").value(own));

        write(put("/api/brands/el-delito/folders"), "{\"items\":[{\"role\":\"DRAFTS\",\"location\":\"relativa\"}]}")
                .andExpect(status().isBadRequest());
        write(put("/api/brands/el-delito/folders"), """
                {"items":[{"role":"DRAFTS","location":"%1$s"},{"role":"DRAFTS","location":"%1$s"}]}""".formatted(json(material)))
                .andExpect(status().isBadRequest());
        write(put("/api/brands/no-existe/folders"), "{\"items\":[]}").andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void publicationsAreCreatedAndEditedRespectingTheirLifecycle() throws Exception {
        String publication = """
                {"platform":"INSTAGRAM","type":"Carrusel","title":"Poema 1","scheduled_on":"2030-01-10",
                 "scheduled_time":"19:00","initial_status":"DRAFT"}""";
        write(post("/api/promotions/lanzamiento-del-poemario/publications"), publication)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("2030-01-10-instagram"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.route").value("Publer"))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.demo").value(false));
        write(post("/api/promotions/lanzamiento-del-poemario/publications"), publication)
                .andExpect(jsonPath("$.id").value("2030-01-10-instagram-2"));
        write(post("/api/promotions/no-existe/publications"), publication).andExpect(status().isNotFound());
        write(post("/api/promotions/lanzamiento-del-poemario/publications"),
                publication.replace("DRAFT", "SCHEDULED")).andExpect(status().isBadRequest());
        write(post("/api/promotions/lanzamiento-del-poemario/publications"),
                publication.replace("19:00", "25:00")).andExpect(status().isBadRequest());

        String edited = publication.replace(",\"initial_status\":\"DRAFT\"", "").replace("Poema 1", "Poema uno");
        write(put("/api/publications/2030-01-10-instagram"), edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Poema uno"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        write(put("/api/publications/2030-01-10-instagram"), publication).andExpect(status().isBadRequest());
        write(put("/api/publications/no-existe"), edited).andExpect(status().isNotFound());

        write(post("/api/publications/2030-01-10-instagram/status-changes"), "{\"status\":\"IN_REVIEW\"}")
                .andExpect(status().isOk());
        // Sin texto ni archivos no pasa el control de calidad, salvo que se acepten los avisos uno a uno
        write(post("/api/publications/2030-01-10-instagram/status-changes"), "{\"status\":\"APPROVED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("control de calidad: 2 avisos")));
        mvc.perform(get("/api/publications/2030-01-10-instagram/quality"))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.issues[0].key").value("TEXT_MISSING"))
                .andExpect(jsonPath("$.issues[1].key").value("FILES_MISSING"))
                .andExpect(jsonPath("$.issues[1].source").value("CODE"));
        write(post("/api/publications/2030-01-10-instagram/quality-acceptances"), "{\"key\":\"TEXT_MISSING\",\"reason\":\"x\"}")
                .andExpect(status().isBadRequest());
        write(post("/api/publications/2030-01-10-instagram/quality-acceptances"), "{\"key\":\"OTRO\",\"reason\":\"Prueba\"}")
                .andExpect(status().isBadRequest());
        for (String key : new String[] {"TEXT_MISSING", "FILES_MISSING"}) {
            write(post("/api/publications/2030-01-10-instagram/quality-acceptances"),
                    "{\"key\":\"" + key + "\",\"reason\":\"Prueba del ciclo de vida\"}").andExpect(status().isOk());
        }
        mvc.perform(get("/api/publications/2030-01-10-instagram/quality"))
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.issues[0].accepted").value(true))
                .andExpect(jsonPath("$.issues[0].accepted_reason").value("Prueba del ciclo de vida"));
        for (String next : new String[] {"APPROVED", "SCHEDULED"}) {
            write(post("/api/publications/2030-01-10-instagram/status-changes"), "{\"status\":\"" + next + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
        }
        write(post("/api/publications/2030-01-10-instagram/status-changes"), "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isConflict());
        write(post("/api/publications/2030-01-10-instagram-2/status-changes"), "{\"status\":\"SCHEDULED\"}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/promotions/lanzamiento-del-poemario/publications"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @Order(6)
    void theBrandIdentityIsSavedClean() throws Exception {
        mvc.perform(get("/api/brands/el-delito/identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tone").value(""))
                .andExpect(jsonPath("$.forbidden_expressions.length()").value(0));
        write(put("/api/brands/el-delito/identity"), """
                {"tone":" Cercano y con humor ","audience":"Lectores de poesía","forbidden_topics":["política"],
                 "preferred_expressions":["poemario-objeto"],"forbidden_expressions":["compra ya"," ","compra ya"],
                 "colors":["#D23A2E"],"font":"DejaVu Serif","logo":"","visual_style":"","photo_style":"",
                 "call_to_action":"Comenta DELITO y te mando el enlace","fixed_hashtags":"#eldelito"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tone").value("Cercano y con humor"))
                .andExpect(jsonPath("$.forbidden_expressions.length()").value(1));
        mvc.perform(get("/api/brands/el-delito/identity")).andExpect(jsonPath("$.fixed_hashtags").value("#eldelito"));
        mvc.perform(get("/api/brands/no-existe/identity")).andExpect(status().isNotFound());
    }

    private static String json(String text) { return text.replace("\\", "\\\\"); }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
