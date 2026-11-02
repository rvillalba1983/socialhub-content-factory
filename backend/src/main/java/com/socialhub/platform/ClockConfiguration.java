package com.socialhub.platform;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    /** Hora de Madrid: todas las fechas de programación de las promociones van en Europe/Madrid. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
