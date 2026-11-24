package com.socialhub.brandkit.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Pinta la vista previa de una plantilla con sus textos de ejemplo. Nunca lanza una excepción por una plantilla mala. */
@SecondaryPort
public interface TemplatePreviewer {

    /** PNG ({@code null} si no se pudo pintar), avisos y errores, en español. */
    record Result(byte @org.jspecify.annotations.Nullable [] png, List<String> warnings, List<String> errors) {}

    Result preview(String svg, int pixelWidth);
}
