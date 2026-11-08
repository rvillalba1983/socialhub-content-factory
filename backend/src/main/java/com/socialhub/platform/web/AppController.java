package com.socialhub.platform.web;

import com.socialhub.api.AppApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Cerrar SocialHub desde la web (no tiene ventana propia). */
@Slf4j
@RestController
@RequiredArgsConstructor
class AppController implements AppApi {

    private final ConfigurableApplicationContext context;

    @Override
    public ResponseEntity<Void> shutdownApp(String requestGuard) {
        log.info("SocialHub se cierra a petición desde la web");
        // Después de responder: se cierra Spring (motor, PostgreSQL, servidor) y termina el programa
        Thread.ofPlatform().name("cierre").start(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        });
        return ResponseEntity.accepted().build();
    }
}
