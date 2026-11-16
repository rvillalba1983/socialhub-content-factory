package com.socialhub.flows;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Prueba de flujo: arranca SocialHub entero (PostgreSQL incluido, migraciones, modo real sin simulador) con el reloj
 * de {@link FlowClock} y los bucles internos rápidos. Solo se simula lo de fuera: Claude y Drive (carpetas y archivos
 * en disco), Meta y Publora (un servidor HTTP local). El contexto se cierra al acabar la clase para no dejar
 * PostgreSQL abiertos.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL",
        "socialhub.engine.poll-millis=200",
        "socialhub.engine.initial-delay-millis=200",
        "socialhub.ai.initial-delay-millis=300",
        "socialhub.ai.dispatch-millis=300",
        "socialhub.ai.read-millis=300",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.publishing.initial-delay-millis=3600000",
        "socialhub.instagram.poll-millis=10",
        "socialhub.instagram.max-polls=5"
})
@AutoConfigureMockMvc
@Import(FlowClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface FlowTest {
}
