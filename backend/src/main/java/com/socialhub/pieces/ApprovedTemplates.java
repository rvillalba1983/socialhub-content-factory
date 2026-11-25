package com.socialhub.pieces;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Puerto de salida: las plantillas del kit de marca APROBADO. Lo implementa el módulo del kit de marca (que ya depende de
 * este), así que las piezas no dependen del kit. Vive en la raíz del módulo, y no en {@code domain}, para que el otro módulo
 * pueda implementarlo: de otro módulo solo se ve la raíz.
 */
@SecondaryPort
public interface ApprovedTemplates {

    /**
     * Dónde va la ilustración, en unidades del {@code viewBox} de la plantilla.
     */
    record Zone(double x, double y, double width, double height) {}

    /**
     * Una plantilla del kit aprobado.
     *
     * @param id       identificador (por ejemplo {@code cierre-4x5})
     * @param piece    pieza para la que sirve: {@code cita}, {@code cierre} o {@code historia}
     * @param svg      el SVG completo (no se envía a Claude)
     * @param image    zona de la ilustración; vacía en la cita si no lleva
     * @param required campos de texto obligatorios (nombre sin {@code campo-})
     * @param optional campos opcionales
     * @param fixed    campos fijos (siempre llevan el texto de la plantilla; Claude no los manda)
     * @param ratio    proporción, como «4:5» o «9:16» (el ancho y el alto del {@code viewBox} si es otra)
     * @param width    ancho del {@code viewBox}
     * @param height   alto del {@code viewBox}
     */
    record Template(String id, String piece, String name, String svg, @Nullable Zone image, List<String> required,
                    List<String> optional, List<String> fixed, String ratio, int width, int height) {}

    /** Las plantillas del kit aprobado de la marca; vacía si no tiene kit aprobado. */
    List<Template> of(String brandId);

    /** Una plantilla del kit aprobado por su identificador. */
    default Optional<Template> find(String brandId, String templateId) {
        return of(brandId).stream().filter(t -> t.id().equals(templateId)).findFirst();
    }
}
