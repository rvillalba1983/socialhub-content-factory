package com.socialhub;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj de pruebas que no depende del día en que se ejecutan: arranca en un momento fijo (por defecto, un martes a las
 * 10:00 de Madrid) y a partir de ahí avanza con el tiempo real, de modo que las esperas y los plazos siguen funcionando.
 * Hace falta porque la aplicación pide sola cosas según el día de la semana (p. ej. las propuestas de la semana siguiente
 * desde el jueves), y eso cambiaba el número de tareas y gastaba el límite diario de encargos según el día.
 * Se puede probar otro momento con {@code -Dsocialhub.test.now=2026-11-12T19:00} (hora de Madrid).
 */
@TestConfiguration
public class PinnedClock {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    public static final String DEFAULT_NOW = "2026-11-10T10:00:00";

    @Bean
    @Primary
    Clock pinnedClock() {
        Instant target = LocalDateTime.parse(System.getProperty("socialhub.test.now", DEFAULT_NOW)).atZone(MADRID).toInstant();
        return Clock.offset(Clock.system(MADRID), Duration.between(Instant.now(), target));
    }
}
