package com.socialhub.publishing.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** API REST de Publora (https://docs.publora.com). Cada llamada lleva la clave de Raúl. */
@SecondaryPort
public interface PubloraGateway {

    /** Una cuenta conectada en Publora. {@code id} tiene la forma «instagram-123». */
    record Connection(String id, String platform, @Nullable String username, @Nullable String displayName, boolean usable) {
        public String label() {
            String name = username != null ? username : displayName != null ? displayName : id;
            return platform + " · " + name;
        }
    }

    record Media(Path file, String contentType, boolean video) {}

    /** Estado de lo programado: el del grupo y el de la red (con su identificador y enlace cuando está publicada). */
    record PostState(String status, @Nullable String postedId, @Nullable String permalink, @Nullable String error) {}

    /** Problema de Publora, con su mensaje para Raúl. */
    class PubloraException extends RuntimeException {
        public PubloraException(String message) { super(message); }
    }

    List<Connection> connections(String key);

    /**
     * Crea el borrador, sube los archivos y lo programa a esa hora. Si algo falla por el camino, borra el borrador.
     *
     * @return identificador del grupo («postGroupId»)
     */
    String schedule(String key, String connectionId, String content, Map<String, Object> platformSettings,
                    List<Media> media, Instant at, String idempotencyKey);

    PostState post(String key, String postGroupId);

    void delete(String key, String postGroupId);
}
