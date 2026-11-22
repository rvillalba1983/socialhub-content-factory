package com.socialhub.pieces.domain;

import java.awt.image.BufferedImage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Resultado de pintar una plantilla: la imagen (nula si hay errores) y los avisos y errores, en español. */
public record TemplateRender(@Nullable BufferedImage image, List<String> warnings, List<String> errors) {

    public boolean ok() {
        return image != null && errors.isEmpty();
    }
}
