package com.socialhub.platform.process;

/** Un programa local terminó con error o se agotó su tiempo. Lleva el final de su salida para el registro. */
public class ProcessFailedException extends RuntimeException {

    private final String output;

    public ProcessFailedException(String message, String output) {
        super(message);
        this.output = output;
    }

    public String output() { return output; }
}
