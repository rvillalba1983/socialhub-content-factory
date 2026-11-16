package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Prueba con el registro real: se arranca Spring con el application.yml de verdad y el registro en archivo activado
 * (como en la aplicación instalada), se escriben mensajes con secretos y se lee el archivo. Ningún secreto puede estar
 * en él, ni en el mensaje ni en la traza de una excepción; el texto normal sí.
 */
class LogFileRedactionTest {

    @Configuration
    static class Empty {}

    @Test
    void socialhubLogNoLlevaSecretos(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("logs").resolve("socialhub.log");
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties("logging.file.name=" + file, "spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run()) {
            Logger log = LoggerFactory.getLogger("com.socialhub.prueba");
            for (Secrets.Sample s : Secrets.SAMPLES) log.warn("mensaje: {}", s.text());
            for (String text : Secrets.HARMLESS) log.info(text);
            Secrets.Sample withTrace = Secrets.SAMPLES.getFirst();
            log.error("fallo", new IllegalStateException("excepción con " + Secrets.SAMPLES.get(1).text(),
                    new RuntimeException("causa con " + withTrace.text())));
        } finally {
            LoggingSystem.get(getClass().getClassLoader()).cleanUp();
        }

        String text = Files.readString(file);
        for (Secrets.Sample s : Secrets.SAMPLES) assertThat(text).as(s.text()).doesNotContain(s.secret());
        for (String harmless : Secrets.HARMLESS) assertThat(text).contains(harmless);
        assertThat(text).contains("password=***").contains("IllegalStateException: excepción con llamada con token=***")
                .contains("causa con Authorization: Bearer ***");
    }
}
