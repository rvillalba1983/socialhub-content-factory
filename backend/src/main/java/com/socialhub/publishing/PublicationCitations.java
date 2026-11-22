package com.socialhub.publishing;

import java.util.ArrayList;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Las citas (versos, letras…) que Claude declara en una publicación. SocialHub comprueba en el control de calidad que cada
 * una aparece literalmente en un documento de las carpetas de la promoción.
 */
@PrimaryPort
public interface PublicationCitations {

    int MAX_PER_PUBLICATION = 20;
    int MAX_TEXT = 2000;

    /** Una cita tal como la devuelve Claude: {@code {text, source_file, location}}. */
    record Declared(String text, String sourceFile, @Nullable String location) {}

    /** Sustituye las citas de la publicación por estas (una lista vacía las quita). */
    void declare(String publicationId, List<Declared> citations);

    /**
     * Lee una lista {@code [{text, source_file, location}]} del JSON de Claude; ignora lo que no tenga texto o documento.
     *
     * @throws IllegalArgumentException si hay demasiadas citas o alguna es demasiado larga
     */
    static List<Declared> parse(JsonNode array) {
        List<Declared> list = new ArrayList<>();
        if (!array.isArray()) return list;
        for (JsonNode item : array) {
            Declared d = parseOne(item);
            if (d != null) list.add(d);
        }
        if (list.size() > MAX_PER_PUBLICATION) {
            throw new IllegalArgumentException("Demasiadas citas en una publicación (máximo " + MAX_PER_PUBLICATION + ")");
        }
        return list;
    }

    /** Una cita del JSON; nulo si no trae texto o documento. */
    static @Nullable Declared parseOne(JsonNode item) {
        String text = item.path("text").asString("").strip();
        String file = item.path("source_file").asString("").strip();
        String location = item.path("location").isString() ? item.path("location").asString().strip() : null;
        if (text.isEmpty() || file.isEmpty()) return null;
        if (text.length() > MAX_TEXT || file.length() > 500 || (location != null && location.length() > 200)) {
            throw new IllegalArgumentException("Una cita es demasiado larga (texto de hasta " + MAX_TEXT + " caracteres)");
        }
        return new Declared(text, file, location == null || location.isEmpty() ? null : location);
    }
}
