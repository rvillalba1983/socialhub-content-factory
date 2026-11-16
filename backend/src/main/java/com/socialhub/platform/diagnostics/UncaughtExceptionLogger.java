package com.socialhub.platform.diagnostics;

import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manejador global de excepciones que ningún código captura: el hilo muere y, sin esto, solo quedaría una línea en la
 * consola (que en la aplicación instalada no existe). Deja la traza en {@code arranque.log} y en {@code socialhub.log}.
 * Si es falta de memoria, añade cuánta había. Nunca lanza una excepción.
 */
public final class UncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UncaughtExceptionLogger.class);
    private final BiConsumer<String, Throwable> sink;

    UncaughtExceptionLogger(BiConsumer<String, Throwable> sink) {
        this.sink = sink;
    }

    /** Lo pone como manejador de todos los hilos. */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger((message, error) -> {
            StartupLog.error(message, error);
            log.error(message, error);
        }));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            sink.accept(describe(thread, error), error);
        } catch (Throwable ignored) {
            // Un fallo al registrar no puede esconder el original ni matar otro hilo
        }
    }

    static String describe(@Nullable Thread thread, Throwable error) {
        String message = "Excepción no capturada en el hilo «" + (thread == null ? "?" : thread.getName()) + "»";
        if (hasCause(error, OutOfMemoryError.class)) {
            Runtime rt = Runtime.getRuntime();
            message += ". Falta de memoria: en uso " + mb(rt.totalMemory() - rt.freeMemory()) + " MB de un máximo de "
                    + mb(rt.maxMemory()) + " MB (no se guarda volcado de memoria: pesa gigas y contiene datos y claves)";
        }
        return message;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private static long mb(long bytes) { return bytes / (1024 * 1024); }
}
