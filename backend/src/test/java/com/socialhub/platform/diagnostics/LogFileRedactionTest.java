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
        // Si otra prueba de este mismo proceso ya inició el registro, Spring Boot no lo reconfigura: se le hace olvidarlo
        LoggingSystem.get(getClass().getClassLoader()).cleanUp();
        ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).removeObject(LoggingSystem.class.getName());
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties(java.util.Map.<String, Object>of("logging.file.name", file.toString(),
                        "spring.main.banner-mode", "off", "spring.main.log-startup-info", "false"))
                .run()) {
            Logger log = LoggerFactory.getLogger("com.socialhub.prueba");
            for (Secrets.Sample s : Secrets.SAMPLES) log.warn("mensaje: {}", s.text());
            for (String text : Secrets.HARMLESS) log.info(text);
            Secrets.Sample withTrace = Secrets.SAMPLES.getFirst();
            log.error("fallo", new IllegalStateException("excepción con " + Secrets.SAMPLES.get(1).text(),
                    new RuntimeException("causa con " + withTrace.text())));
        } finally {
            // Windows no deja borrar la carpeta temporal con el archivo abierto: se cierra el registro en archivo
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            java.util.List<ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent>> all = new java.util.ArrayList<>();
            root.iteratorForAppenders().forEachRemaining(all::add);
            for (var appender : all) {
                if (appender instanceof ch.qos.logback.core.FileAppender<?>) {
                    appender.stop();
                    root.detachAppender(appender);
                }
            }
            LoggingSystem.get(getClass().getClassLoader()).cleanUp();
        }

        String text = Files.readString(file);
        for (Secrets.Sample s : Secrets.SAMPLES) assertThat(text).as(s.text()).doesNotContain(s.secret());
        for (String harmless : Secrets.HARMLESS) assertThat(text).contains(harmless);
        assertThat(text).contains("password=***").contains("IllegalStateException: excepción con llamada con token=***")
                .contains("causa con Authorization: Bearer ***");
    }
}
