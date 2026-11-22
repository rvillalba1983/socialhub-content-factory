package com.socialhub.pieces;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Aviso previo de material: qué publicaciones de una promoción, de las que van a producirse (en producción, sin piezas y fuera
 * de cualquier spec pedido), no tienen imagen ni vídeo propio en las carpetas de Material. Es solo informativo: sin material,
 * las piezas usan el fondo de Claude, la portada de la marca o un fondo de SocialHub.
 */
@PrimaryPort
public interface MissingMaterial {

    /**
     * @param accessible false si ninguna carpeta de material está disponible en este ordenador (entonces no se sabe qué falta)
     * @param pending cuántas publicaciones van a producirse
     * @param missing los títulos de las que no tienen material propio (vacío si no es accesible)
     * @param cover nombre del archivo de portada de la marca si existe (por ejemplo «portada.jpg»)
     * @param visualStyle estilo visual de la promoción; vacío si no tiene
     */
    record Report(boolean accessible, int pending, List<String> missing, @Nullable String cover, String visualStyle) {}

    /** Informe de la promoción; sin publicaciones pendientes, {@code pending} es 0. */
    Report of(String promotionId);
}
