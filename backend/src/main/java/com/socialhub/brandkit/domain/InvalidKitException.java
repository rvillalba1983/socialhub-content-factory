package com.socialhub.brandkit.domain;

import java.util.List;

/** El kit no vale: lleva todos los motivos, en español (la API responde 400). */
public class InvalidKitException extends IllegalArgumentException {

    private final transient List<String> errors;

    public InvalidKitException(List<String> errors) {
        super("El kit no es válido: " + String.join(" ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() { return errors; }
}
