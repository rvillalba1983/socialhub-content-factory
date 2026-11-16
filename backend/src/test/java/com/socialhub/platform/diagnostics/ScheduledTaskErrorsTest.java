package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Una tarea programada que lanza una excepción no se pierde: el planificador de Spring la deja en el registro como
 * ERROR y la tarea sigue programada. (Es el comportamiento de Spring; esta prueba avisa si algún día deja de serlo.)
 */
class ScheduledTaskErrorsTest {

    @Test
    void laExcepcionDeUnaTareaProgramadaSeRegistraComoError() throws InterruptedException {
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(events);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        CountDownLatch twice = new CountDownLatch(2);
        try {
            scheduler.scheduleWithFixedDelay(() -> {
                twice.countDown();
                throw new IllegalStateException("fallo de la tarea de prueba");
            }, Duration.ofMillis(20));
            assertThat(twice.await(5, TimeUnit.SECONDS)).as("la tarea sigue programada tras fallar").isTrue();
        } finally {
            scheduler.shutdown();
            root.detachAppender(events);
        }
        assertThat(events.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getThrowableProxy().getMessage()).contains("fallo de la tarea de prueba");
        });
    }
}
