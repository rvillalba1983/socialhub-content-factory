package com.socialhub.pieces.domain;

import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Motor de plantillas SVG (formato en {@code docs/plantillas-svg.md}). Nunca lanza una excepción por una plantilla
 * mala: la devuelve en {@code errors}.
 */
@SecondaryPort
public interface TemplateRenderer {

    /** Revisa la plantilla y devuelve su tamaño, sus campos y todos los errores que encuentre. */
    TemplateCheck check(String templateSvg);

    /** Pinta la plantilla con los textos de cada campo (nombre sin {@code campo-}) al tamaño pedido en píxeles. */
    TemplateRender render(String templateSvg, Map<String, String> texts, int width, int height);

    /**
     * Como {@link #render} pero la imagen es ARGB y lo que la plantilla no dibuja queda transparente, para ponerla encima de
     * la ilustración de la publicación (decisión 53).
     */
    TemplateRender renderOver(String templateSvg, Map<String, String> texts, int width, int height);

    /** Como {@link #render} pero con el texto de ejemplo de cada campo. */
    TemplateRender preview(String templateSvg, int width, int height);
}
