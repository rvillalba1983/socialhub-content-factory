package com.socialhub.publishing.domain;

import java.nio.file.Path;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Lee el texto de un documento de la promoción (txt, md y PDF con texto), con límites de tamaño, páginas y tiempo. */
@SecondaryPort
public interface DocumentReader {

    /**
     * @param readable false si no se puede leer el texto (imágenes, PDF escaneado, formato sin soporte, demasiado grande…)
     * @param unit lo que son las partes: {@code "página"} (PDF) o {@code "línea"} (texto plano)
     * @param parts el texto por páginas (PDF) o una sola parte con todo el texto (txt, md)
     * @param reason por qué no se pudo leer, en español
     */
    record Reading(boolean readable, String unit, List<String> parts, @Nullable String reason) {
        public static Reading unreadable(String reason) { return new Reading(false, "", List.of(), reason); }
    }

    Reading read(Path file);
}
