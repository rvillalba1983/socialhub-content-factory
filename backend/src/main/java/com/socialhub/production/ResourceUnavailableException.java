package com.socialhub.production;

/**
 * Un ejecutor no puede trabajar porque falta una carpeta (por ejemplo, Google Drive para ordenador cerrado y sin G:).
 * La tarea queda bloqueada con el motivo, sin gastar intentos, y vuelve sola a la cola cuando la carpeta aparece.
 */
public class ResourceUnavailableException extends RuntimeException {

    private final String path;

    /**
     * @param path carpeta que se espera
     * @param message motivo que verá Raúl
     */
    public ResourceUnavailableException(String path, String message) {
        super(message);
        this.path = path;
    }

    public String path() { return path; }
}
