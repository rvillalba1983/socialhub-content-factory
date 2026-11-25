package com.socialhub.publishing.domain;

import java.nio.file.Path;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/**
 * API oficial de Instagram (Meta Graph API con inicio de sesión unificado). Cada llamada lleva la clave de usuario del
 * sistema de Raúl, que nunca se escribe en los registros.
 */
@SecondaryPort
public interface InstagramGateway {

    /** Quién es la clave (GET /me). */
    record Identity(String id, @Nullable String name) {}

    /** Cuenta de Instagram (GET /{ig-id}?fields=id,username). */
    record Account(String id, String username) {}

    /** Publicaciones por la API en las últimas 24 h y su límite. */
    record Quota(int used, int total) {
        public boolean exhausted() { return used >= total; }
    }

    /**
     * Métricas de una publicación (Graph API Insights, v23.0). Lo que Meta no devuelve para esa publicación (por ejemplo,
     * me gusta ocultos o una métrica que no admite ese tipo) es {@code null}: nunca se estima.
     */
    record MediaInsights(@Nullable Long views, @Nullable Long reach, @Nullable Long likes, @Nullable Long comments,
                         @Nullable Long saves, @Nullable Long shares) {}

    /**
     * Un sonido de la biblioteca de música de Instagram (Instagram Audio API).
     *
     * @param id     {@code audio_id}, el que se adjunta al reel
     * @param title  título
     * @param artist artista ({@code display_artist}), si Meta lo da
     */
    record Audio(String id, String title, @Nullable String artist) {}

    enum ContainerState { IN_PROGRESS, FINISHED, ERROR, EXPIRED, PUBLISHED }

    record Container(ContainerState state, @Nullable String detail) {}

    /**
     * Problema con Meta, con su mensaje para Raúl.
     * <ul>
     *   <li>{@code INVALID_KEY}: Meta no acepta la clave.</li>
     *   <li>{@code REJECTED}: Meta respondió con un error: la llamada no tuvo efecto.</li>
     *   <li>{@code UNCERTAIN}: sin respuesta fiable (tiempo agotado, corte, error 5xx): no se sabe si Meta la procesó.</li>
     * </ul>
     */
    class InstagramException extends RuntimeException {
        public enum Kind { INVALID_KEY, REJECTED, UNCERTAIN }

        private final Kind kind;

        public InstagramException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public Kind kind() { return kind; }
    }

    Identity me(String key);

    Account account(String key, String igUserId);

    Quota publishingLimit(String key, String igUserId);

    /** POST /{ig-id}/media con esos parámetros. Devuelve el identificador del contenedor. */
    String createContainer(String key, String igUserId, Map<String, String> params);

    /** Subida resumible del vídeo al contenedor (rupload.facebook.com). */
    void upload(String key, String containerId, Path file);

    Container container(String key, String containerId);

    /**
     * Busca música en la biblioteca de Instagram (Audio API, solo con inicio de sesión de Facebook): GET /ig_audio con
     * {@code audio_type=music}, {@code user_id} y {@code search_query}. Solo devuelve sonidos autorizados para terceros, así
     * que puede diferir de lo que se ve en la app. Vacío si no hay ninguno.
     */
    java.util.List<Audio> searchMusic(String key, String igUserId, String query);

    /** POST /{ig-id}/media_publish. Un {@code UNCERTAIN} aquí significa que puede haberse publicado. */
    String publish(String key, String igUserId, String containerId);

    /** GET /{media-id}?fields=permalink. */
    @Nullable String permalink(String key, String mediaId);

    /**
     * Métricas de una publicación ya publicada por la API: GET /{media-id}/insights y los contadores de la publicación.
     * Falla (con el motivo) si Meta no da ninguna, por ejemplo por falta de permisos de la clave.
     */
    MediaInsights mediaInsights(String key, String mediaId);
}
