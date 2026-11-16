package com.socialhub.platform.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El manejador global deja constancia del hilo y la excepción, avisa si es falta de memoria y nunca falla. */
class UncaughtExceptionLoggerTest {

    record Entry(String message, Throwable error) {}

    @Test
    void registraElHiloYLaExcepcion() throws InterruptedException {
        List<Entry> seen = new ArrayList<>();
        Thread t = new Thread(() -> { throw new IllegalStateException("se rompió"); }, "tarea-rara");
        t.setUncaughtExceptionHandler(new UncaughtExceptionLogger((m, e) -> seen.add(new Entry(m, e))));
        t.start();
        t.join();
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().message()).contains("«tarea-rara»").doesNotContain("memoria");
        assertThat(seen.getFirst().error()).hasMessage("se rompió");
    }

    @Test
    void laFaltaDeMemoriaLlevaLosDatosDeMemoriaYNoPideVolcado() {
        List<Entry> seen = new ArrayList<>();
        new UncaughtExceptionLogger((m, e) -> seen.add(new Entry(m, e)))
                .uncaughtException(Thread.currentThread(), new RuntimeException("envuelta", new OutOfMemoryError("Java heap space")));
        assertThat(seen.getFirst().message()).contains("Falta de memoria").contains("MB de un máximo de").contains("no se guarda volcado");
    }

    @Test
    void siRegistrarFallaNoLanzaNada() {
        new UncaughtExceptionLogger((m, e) -> { throw new IllegalStateException("disco lleno"); })
                .uncaughtException(Thread.currentThread(), new RuntimeException("x"));
    }

    @Test
    void instalarloLoDejaComoManejadorPorDefecto() {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            UncaughtExceptionLogger.install();
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isInstanceOf(UncaughtExceptionLogger.class);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }
}
