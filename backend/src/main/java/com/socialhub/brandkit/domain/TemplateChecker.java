package com.socialhub.brandkit.domain;

import java.util.List;

/**
 * Revisa una plantilla SVG con el motor de plantillas. No es un puerto de salida propio: lo atiende la aplicación con el
 * puerto primario del módulo de piezas. Nunca lanza una excepción por una plantilla mala.
 */
public interface TemplateChecker {

    /**
     * @param fields campos de texto que rellena Claude (nombre sin {@code campo-})
     * @param fixed  campos fijos, con el texto de la plantilla
     * @param errors motivos, en español, por los que la plantilla no sirve
     */
    record Result(int width, int height, List<String> fields, List<String> fixed, List<String> errors) {}

    Result check(String svg);
}
