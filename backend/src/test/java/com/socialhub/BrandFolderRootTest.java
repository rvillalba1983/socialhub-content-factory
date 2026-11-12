package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Carpetas de la marca desde una carpeta raíz: estructura, idempotencia, lo existente se respeta y raíz inaccesible. */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL"
})
@AutoConfigureMockMvc
class BrandFolderRootTest {

    @TempDir Path drive;
    @Autowired MockMvc mvc;

    private ResultActions write(String url, String body) throws Exception {
        return mvc.perform(post(url).header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void brand(String name) throws Exception {
        write("/api/brands", "{\"name\":\"" + name + "\",\"handle\":\"@" + name + "\",\"color\":\"#C98A1B\"}")
                .andExpect(status().isCreated());
    }

    private static String json(Path p) {
        return "{\"base\":\"" + p.toString().replace("\\", "\\\\") + "\"}";
    }

    @Test
    void createsStructureFromRootRespectingWhatExists() throws Exception {
        brand("rootone");
        String url = "/api/brands/rootone/folders/creation";
        Path root = drive.resolve("socialhub").resolve("eldelito");
        // Una carpeta y un archivo que ya existen no se tocan
        Files.createDirectories(root.resolve("borradores"));
        Files.writeString(root.resolve("borradores").resolve("a.txt"), "hola");

        write(url, json(root)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[?(@.role == 'MATERIAL')].location").value(root.toString()))
                .andExpect(jsonPath("$.items[?(@.role == 'ORDERS')].location").value(root.resolve("encargos").toString()))
                .andExpect(jsonPath("$.items[?(@.available == false)]").isEmpty());
        for (String name : new String[] {"borradores", "generados", "aprobados", "publicados", "informes", "encargos"}) {
            assertThat(root.resolve(name)).isDirectory();
        }
        for (String name : new String[] {"para-claude", "para-claude-pc", "de-claude", "publico"}) {
            assertThat(root.resolve("encargos").resolve(name)).isDirectory();
        }
        assertThat(root.resolve("borradores").resolve("a.txt")).hasContent("hola");

        // Repetir no cambia nada ni falla
        write(url, json(root)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(7));
        assertThat(root.resolve("borradores").resolve("a.txt")).hasContent("hola");
    }

    @Test
    void inaccessibleRootIsReportedClearly() throws Exception {
        brand("roottwo");
        String url = "/api/brands/roottwo/folders/creation";
        // Una raíz que es un archivo no es accesible
        Path file = Files.writeString(drive.resolve("fichero.txt"), "x");
        write(url, json(file)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No se puede acceder")));
        // Debajo de un archivo no se puede crear nada
        write(url, json(file.resolve("hijo"))).andExpect(status().isConflict());
        // Una ruta relativa no vale
        write(url, "{\"base\":\"relativa/x\"}").andExpect(status().is4xxClientError());
        assertThat(Path.of("relativa")).doesNotExist();
    }
}
