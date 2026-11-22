package com.socialhub.pieces.domain;

import java.util.List;

/** Resultado de revisar una plantilla: su tamaño (del {@code viewBox}), sus campos y los errores que impiden usarla. */
public record TemplateCheck(int width, int height, List<TemplateField> fields, List<String> errors) {

    public boolean valid() {
        return errors.isEmpty();
    }
}
