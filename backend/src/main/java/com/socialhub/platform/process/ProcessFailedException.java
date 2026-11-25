package com.socialhub.platform.process;

import org.jspecify.annotations.Nullable;

/**
 * Un programa local terminó con error, no pudo lanzarse o se agotó su tiempo. Lleva el final de su salida para el registro
 * y, si el programa llegó a terminar, su código de salida.
 */
public class ProcessFailedException extends RuntimeException {

    private final String output;
    private final @Nullable Integer exitCode;

    public ProcessFailedException(String message, String output) {
        this(message, output, null);
    }

    public ProcessFailedException(String message, String output, @Nullable Integer exitCode) {
        super(message);
        this.output = output;
        this.exitCode = exitCode;
    }

    public String output() { return output; }

    /** Código de salida si el programa terminó por sí mismo; {@code null} si no se pudo lanzar o se agotó el tiempo. */
    public @Nullable Integer exitCode() { return exitCode; }
}
