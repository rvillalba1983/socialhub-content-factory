package com.socialhub.publishing.application;

import com.socialhub.publishing.application.InstagramRoute;
import com.socialhub.publishing.domain.SecretStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las credenciales de la API oficial de Instagram, todas en la carpeta de datos del ordenador (nunca en la base de datos):
 * <ul>
 *   <li>la clave de «usuario del sistema» pegada a mano ({@code instagram-api}): no caduca;</li>
 *   <li>el App ID y el App Secret de la app de Meta ({@code instagram-app-id}, {@code instagram-app-secret});</li>
 *   <li>la conexión por «Conectar con Instagram» ({@code instagram-oauth}): clave de larga duración, su caducidad y quién
 *       se conectó.</li>
 * </ul>
 * La ruta de publicación usa la clave activa: la conexión mientras no haya caducado y, si no, la de usuario del sistema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InstagramCredentials {

    static final String SYSTEM_KEY = InstagramRoute.KEY;
    static final String APP_ID = "instagram-app-id";
    static final String APP_SECRET = "instagram-app-secret";
    static final String OAUTH = "instagram-oauth";

    /** La conexión por inicio de sesión de Meta. */
    record Connection(String token, Instant expiresAt, @Nullable String name, List<String> usernames) {
        boolean expired(Instant now) { return !expiresAt.isAfter(now); }

        /** Días enteros que quedan (0 si caduca hoy o ya caducó). */
        long daysLeft(Instant now) { return Math.max(0, Duration.between(now, expiresAt).toDays()); }
    }

    private final SecretStore secrets;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    // ---------------------------------------------------------------- Clave de usuario del sistema

    Optional<String> systemKey() { return secrets.get(SYSTEM_KEY); }

    // ---------------------------------------------------------------- App de Meta

    Optional<String> appId() { return secrets.get(APP_ID); }

    Optional<String> appSecret() { return secrets.get(APP_SECRET); }

    void saveApp(String appId, @Nullable String appSecret) {
        secrets.put(APP_ID, appId);
        if (appSecret != null) secrets.put(APP_SECRET, appSecret);
    }

    void deleteApp() {
        secrets.delete(APP_ID);
        secrets.delete(APP_SECRET);
    }

    // ---------------------------------------------------------------- Conexión por OAuth

    /** La conexión guardada, caducada o no. */
    Optional<Connection> connection() {
        String raw = secrets.get(OAUTH).orElse(null);
        if (raw == null) return Optional.empty();
        try {
            JsonNode node = json.readTree(raw);
            String token = node.path("token").asString("");
            String expires = node.path("expires_at").asString("");
            if (token.isEmpty() || expires.isEmpty()) return Optional.empty();
            List<String> usernames = new ArrayList<>();
            node.path("usernames").forEach(u -> usernames.add(u.asString("")));
            String name = node.path("name").asString("");
            return Optional.of(new Connection(token, Instant.parse(expires), name.isEmpty() ? null : name, List.copyOf(usernames)));
        } catch (JacksonException | java.time.format.DateTimeParseException e) {
            // Nunca se escribe el contenido: lleva la clave
            log.warn("La conexión guardada de Instagram no se puede leer; se ignora");
            return Optional.empty();
        }
    }

    void saveConnection(Connection connection) {
        var node = json.createObjectNode();
        node.put("token", connection.token());
        node.put("expires_at", connection.expiresAt().toString());
        if (connection.name() != null) node.put("name", connection.name());
        var names = node.putArray("usernames");
        connection.usernames().forEach(names::add);
        secrets.put(OAUTH, node.toString());
    }

    void deleteConnection() { secrets.delete(OAUTH); }

    // ---------------------------------------------------------------- La que usa la publicación

    /** La conexión, si existe y no ha caducado. */
    Optional<Connection> validConnection() {
        Instant now = clock.instant();
        return connection().filter(c -> !c.expired(now));
    }

    /** La clave con la que se llama a Meta: la conexión vigente o, si no hay, la de usuario del sistema. */
    Optional<String> activeToken() {
        Optional<String> oauth = validConnection().map(Connection::token);
        return oauth.isPresent() ? oauth : systemKey();
    }
}
