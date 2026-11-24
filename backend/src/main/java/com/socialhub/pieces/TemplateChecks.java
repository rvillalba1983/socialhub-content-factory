package com.socialhub.pieces;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Revisión de plantillas SVG para otros módulos (por ejemplo el kit de marca): usa el motor de plantillas de las piezas. */
@PrimaryPort
public interface TemplateChecks {

    /**
     * Resultado de revisar una plantilla.
     *
     * @param width   ancho del {@code viewBox}
     * @param height  alto del {@code viewBox}
     * @param fields  nombres de los campos de texto (sin {@code campo-}) que el texto de Claude puede rellenar
     * @param fixed   nombres de los campos fijos ({@code data-fijo="true"}): siempre llevan el texto de la plantilla
     * @param errors  motivos, en español, por los que la plantilla no sirve
     */
    record Report(int width, int height, List<String> fields, List<String> fixed, List<String> errors) {}

    /** Revisa la plantilla; nunca lanza una excepción por una plantilla mala. */
    Report check(String templateSvg);

    /** Vista previa: PNG ({@code null} si hay errores) con el texto de ejemplo de cada campo, más avisos y errores en español. */
    record Preview(byte @org.jspecify.annotations.Nullable [] png, List<String> warnings, List<String> errors) {}

    /** Pinta la plantilla con sus textos de ejemplo al ancho pedido (el alto sale de su proporción); nunca lanza por una plantilla mala. */
    Preview preview(String templateSvg, int pixelWidth);
}
