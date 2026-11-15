package com.socialhub.flows;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Pone el {@link FlowClock} como reloj de SocialHub. La prueba lo recibe con {@code @Autowired FlowClock}. */
@TestConfiguration
public class FlowClockConfig {

    @Bean
    @Primary
    FlowClock flowClock() {
        return new FlowClock();
    }
}
