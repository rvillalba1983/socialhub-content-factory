package com.socialhub;

import com.socialhub.platform.desktop.Desktop;
import com.socialhub.platform.diagnostics.StartupLog;
import com.socialhub.platform.diagnostics.StartupPhases;
import com.socialhub.platform.diagnostics.UncaughtExceptionLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@org.springframework.modulith.Modulithic(sharedModules = "platform")
@ConfigurationPropertiesScan
@EnableScheduling
public class SocialHubApplication {

    public static void main(String[] args) {
        UncaughtExceptionLogger.install();
        try {
            // Instalado: si ya está abierto, solo se abre el navegador
            if (Desktop.enabled() && Desktop.prepare()) {
                // Segundo clic: ya se abrió el navegador. Se sale sin esperar a hilos que pudieran quedar vivos
                StartupLog.phase("Segundo arranque: termina");
                System.exit(0);
            }
            SpringApplication app = new SpringApplication(SocialHubApplication.class);
            app.addListeners(new StartupPhases());
            app.run(args);
        } catch (Throwable e) {
            StartupLog.error("SocialHub no ha podido arrancar", e);
            throw e;
        }
    }
}
