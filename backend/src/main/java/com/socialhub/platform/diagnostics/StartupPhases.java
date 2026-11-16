package com.socialhub.platform.diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/** Apunta en {@code arranque.log} las fases del arranque de Spring y, si falla, el motivo. */
public final class StartupPhases implements ApplicationListener<ApplicationEvent> {

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        switch (event) {
            case ApplicationStartingEvent _ -> StartupLog.phase("Spring arrancando");
            case ApplicationPreparedEvent _ -> StartupLog.phase("Configuración leída; se crean las piezas de la aplicación (PostgreSQL y Flyway van a continuación)");
            case WebServerInitializedEvent e -> StartupLog.phase("Servidor web en el puerto " + e.getWebServer().getPort());
            case ApplicationReadyEvent _ -> StartupLog.phase("SocialHub listo");
            case ApplicationFailedEvent e -> StartupLog.error("El arranque ha fallado", e.getException());
            default -> { }
        }
    }

    /** Versión y commit de esta compilación (de build-info.properties); «desconocida» si no se compiló con Maven. */
    public static String version() {
        Properties p = new Properties();
        try (InputStream in = StartupPhases.class.getResourceAsStream("/META-INF/build-info.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            // se queda en «desconocida»
        }
        return p.getProperty("build.version", "desconocida") + " (commit " + p.getProperty("build.commit", "local") + ")";
    }
}
