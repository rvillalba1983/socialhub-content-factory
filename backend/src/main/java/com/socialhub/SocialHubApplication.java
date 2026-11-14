package com.socialhub;

import com.socialhub.platform.desktop.Desktop;
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
        // Instalado: si ya está abierto, solo se abre el navegador
        if (Desktop.enabled() && Desktop.prepare()) return;
        SpringApplication.run(SocialHubApplication.class, args);
    }
}
