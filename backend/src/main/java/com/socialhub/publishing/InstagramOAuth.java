package com.socialhub.publishing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Conexión con Instagram por el flujo OAuth de Meta. */
@PrimaryPort
public interface InstagramOAuth {

    record App(@Nullable String appId, boolean secretSaved, String redirectUri) {}

    record ConnectionInfo(@Nullable String name, List<String> usernames, Instant expiresAt, long daysLeft, boolean expired) {}

    /** {@code source}: OAUTH, SYSTEM_USER o NONE (la que usa la publicación). */
    record Overview(App app, @Nullable ConnectionInfo connection, boolean systemKey, String source) {}

    /** Cuenta de Instagram profesional que cuelga de una página de Facebook de Raúl. */
    record Detected(String igUserId, String username, String pageName, @Nullable String brandId) {}

    record CallbackResult(boolean ok, String message) {}

    /** Aviso de «Qué hacer ahora»: la conexión caduca pronto o ya caducó. */
    record Notice(boolean expired, long daysLeft, boolean systemKeyFallback) {}

    /**
     * Resultado de «Renovar conexión»: {@code renewed} si se renovó sin salir de SocialHub; si no, {@code authorizationUrl} es
     * la dirección de Meta del inicio de sesión completo (la conexión ya caducó o Meta no aceptó la clave).
     */
    record Renewal(boolean renewed, String message, @Nullable String authorizationUrl) {}

    /** La dirección que hay que dar a Meta en «URI de redireccionamiento OAuth válidas», tal cual. */
    String redirectUri();

    Overview overview();

    /**
     * Comprueba con Meta el App ID y el App Secret y los guarda. Con el secreto vacío se conserva el ya guardado (para
     * cambiar solo el App ID).
     */
    Overview saveApp(String appId, @Nullable String appSecret);

    /** Dirección de Meta para dar el permiso, con un {@code state} aleatorio de un solo uso. */
    String start();

    /** Lo que Meta devuelve al navegador. Nunca lanza: el resultado se enseña a Raúl al volver a SocialHub. */
    CallbackResult callback(@Nullable String code, @Nullable String state, @Nullable String error, @Nullable String errorDescription);

    /**
     * «Renovar conexión»: cambia la clave vigente por otra de larga duración con el App ID y el App Secret ya guardados, sin
     * volver a pedírselos a Raúl ni llevarle a Meta. Si la conexión ya caducó, no existe o Meta no acepta la clave, devuelve la
     * dirección del flujo OAuth completo.
     *
     * @throws IllegalArgumentException si faltan el App ID o el App Secret, o Meta no responde (no se pierde la conexión)
     */
    Renewal renew();

    /** Las cuentas de Instagram de las páginas de Raúl (llama a Meta), con la marca a la que está asignada cada una. */
    List<Detected> detected();

    /** Borra la conexión (no toca la clave de usuario del sistema ni las cuentas de las marcas). */
    void disconnect();

    /** La conexión caduca en {@value #WARN_DAYS} días o menos (o ya caducó). */
    Optional<Notice> notice();
}
