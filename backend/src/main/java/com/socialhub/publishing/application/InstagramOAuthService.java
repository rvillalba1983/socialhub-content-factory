package com.socialhub.publishing.application;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.InstagramOAuth;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.MetaOAuthGateway;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * «Conectar con Instagram»: inicio de sesión de Meta (OAuth 2.0) para conseguir la clave de la API oficial sin crear un
 * usuario del sistema. Raúl pega una vez el App ID y el App Secret de su app de Meta (en la carpeta de datos del
 * ordenador, nunca en la base de datos ni en los registros). El flujo:
 * <ol>
 *   <li>{@link #start()} genera un {@code state} aleatorio de un solo uso y devuelve la dirección de Meta;</li>
 *   <li>Meta vuelve a {@link #redirectUri()} con {@code code} y {@code state}; {@link #callback} rechaza un
 *       {@code state} desconocido, caducado o repetido sin llamar a Meta;</li>
 *   <li>cambia el {@code code} por una clave y esta por una de larga duración (60 días), guarda su caducidad, lista las
 *       páginas con su cuenta de Instagram y asigna sola la de cada marca cuando su @usuario coincide.</li>
 * </ol>
 * La clave de usuario del sistema sigue siendo una alternativa que no caduca; ver {@link InstagramCredentials}.
 */
@Slf4j
@Service
class InstagramOAuthService implements InstagramOAuth {

    /** Permisos mínimos para publicar y para encontrar la cuenta de Instagram de cada página. */
    public static final List<String> SCOPES = List.of("instagram_basic", "instagram_content_publish", "pages_show_list",
            "pages_read_engagement");
    /** Lo que dura la clave de larga duración si Meta no dice otra cosa. */
    static final Duration DEFAULT_LIFETIME = Duration.ofDays(60);
    /** Desde cuándo se avisa de que la conexión va a caducar. */
    public static final int WARN_DAYS = 7;
    static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_STATES = 20;

    

    

    

    

    

    

    private final InstagramCredentials credentials;
    private final MetaOAuthGateway oauth;
    private final InstagramGateway instagram;
    private final InstagramAccountRepository accounts;
    private final InstagramService service;
    private final PromotionQueries promotions;
    private final Clock clock;
    private final int port;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    InstagramOAuthService(InstagramCredentials credentials, MetaOAuthGateway oauth, InstagramGateway instagram,
                          InstagramAccountRepository accounts, InstagramService service, PromotionQueries promotions,
                          Clock clock, @Value("${server.port:8686}") int port) {
        this.credentials = credentials;
        this.oauth = oauth;
        this.instagram = instagram;
        this.accounts = accounts;
        this.service = service;
        this.promotions = promotions;
        this.clock = clock;
        this.port = port;
    }

    /** La dirección que hay que dar a Meta en «URI de redireccionamiento OAuth válidas», tal cual. */
    @Override
    public String redirectUri() { return "http://localhost:" + port + "/api/instagram/oauth/callback"; }

    @Override
    public Overview overview() {
        Instant now = clock.instant();
        var app = new App(credentials.appId().orElse(null), credentials.appSecret().isPresent(), redirectUri());
        var connection = credentials.connection().map(c -> new ConnectionInfo(c.name(), c.usernames(), c.expiresAt(),
                c.daysLeft(now), c.expired(now))).orElse(null);
        boolean oauthValid = credentials.validConnection().isPresent();
        boolean system = credentials.systemKey().isPresent();
        return new Overview(app, connection, system, oauthValid ? "OAUTH" : system ? "SYSTEM_USER" : "NONE");
    }

    // ---------------------------------------------------------------- App ID y App Secret

    /**
     * Comprueba con Meta el App ID y el App Secret y los guarda. Con el secreto vacío se conserva el ya guardado (para
     * cambiar solo el App ID).
     */
    @Override
    public Overview saveApp(String appId, @Nullable String appSecret) {
        String id = appId.strip();
        if (!id.matches("\\d{5,30}")) throw new IllegalArgumentException("El App ID es un número (solo cifras): cópialo de tu app en Meta for Developers");
        String secret = appSecret == null || appSecret.isBlank() ? null : appSecret.strip();
        if (secret != null && !secret.matches("[A-Za-z0-9]{16,100}")) {
            throw new IllegalArgumentException("El App Secret tiene que ser letras y números, sin espacios: cópialo entero");
        }
        String effective = secret != null ? secret : credentials.appSecret()
                .orElseThrow(() -> new IllegalArgumentException("Falta el App Secret"));
        try {
            oauth.verifyApp(id, effective);
        } catch (InstagramException e) {
            throw new IllegalArgumentException("No se ha guardado: " + e.getMessage());
        }
        credentials.saveApp(id, secret);
        service.refresh();
        return overview();
    }

    // ---------------------------------------------------------------- Empezar y volver

    /** Dirección de Meta para dar el permiso, con un {@code state} aleatorio de un solo uso. */
    @Override
    public String start() {
        String appId = credentials.appId().orElse(null);
        if (appId == null || credentials.appSecret().isEmpty()) {
            throw new IllegalArgumentException("Primero guarda el App ID y el App Secret de tu app de Meta");
        }
        Instant now = clock.instant();
        states.values().removeIf(created -> created.plus(STATE_LIFETIME).isBefore(now));
        if (states.size() >= MAX_STATES) states.clear();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, now);
        return oauth.authorizationUrl(appId, redirectUri(), state, SCOPES);
    }

    /** Lo que Meta devuelve al navegador. Nunca lanza: el resultado se enseña a Raúl al volver a SocialHub. */
    @Override
    public CallbackResult callback(@Nullable String code, @Nullable String state, @Nullable String error,
                                   @Nullable String errorDescription) {
        // El state es de un solo uso: se gasta aunque Meta traiga un error
        Instant created = state == null ? null : states.remove(state);
        if (created == null || created.plus(STATE_LIFETIME).isBefore(clock.instant())) {
            log.warn("Vuelta de Meta con un state desconocido o caducado: se rechaza sin llamar a Meta");
            return new CallbackResult(false, "La respuesta de Meta no es de una conexión que hayas empezado aquí (o tardó más de 10 minutos). "
                    + "Pulsa «Conectar con Instagram» otra vez.");
        }
        if (error != null) {
            log.info("Meta devolvió un error en el inicio de sesión: {} ({})", error, errorDescription);
            return new CallbackResult(false, "access_denied".equals(error)
                    ? "No has dado el permiso en Meta, así que no se ha conectado nada."
                    : "Meta no ha completado la conexión (" + error + "). Inténtalo de nuevo.");
        }
        if (code == null || code.isBlank()) return new CallbackResult(false, "Meta no devolvió el código de autorización. Inténtalo de nuevo.");
        String appId = credentials.appId().orElse(null);
        String secret = credentials.appSecret().orElse(null);
        if (appId == null || secret == null) return new CallbackResult(false, "Falta el App ID o el App Secret: guárdalos y vuelve a conectar.");
        try {
            var shortLived = oauth.exchangeCode(appId, secret, redirectUri(), code);
            var longLived = oauth.toLongLived(appId, secret, shortLived.value());
            Instant expires = clock.instant().plus(longLived.expiresInSeconds() == null ? DEFAULT_LIFETIME
                    : Duration.ofSeconds(longLived.expiresInSeconds()));
            String token = longLived.value();
            var pages = oauth.pages(token);
            String name = null;
            try {
                name = instagram.me(token).name();
            } catch (InstagramException e) {
                log.info("No se pudo leer el nombre de quien se conectó: {}", e.getMessage());
            }
            var found = pages.stream().filter(MetaOAuthGateway.PageAccount::hasInstagram).toList();
            credentials.saveConnection(new InstagramCredentials.Connection(token, expires, name,
                    found.stream().map(MetaOAuthGateway.PageAccount::username).distinct().toList()));
            List<String> assigned = autoAssign(found);
            service.refresh();
            return new CallbackResult(true, summary(found, assigned));
        } catch (InstagramException e) {
            return new CallbackResult(false, e.getMessage());
        }
    }

    private static String summary(List<MetaOAuthGateway.PageAccount> found, List<String> assigned) {
        if (found.isEmpty()) {
            return "Conectado, pero Meta no encontró ninguna cuenta de Instagram profesional vinculada a tus páginas de Facebook. "
                    + "Vincula la cuenta a una página y vuelve a conectar, o pon el identificador a mano en la ficha de la marca.";
        }
        String accounts = String.join(", ", found.stream().map(a -> "@" + a.username()).toList());
        return "Conectado con Meta. Cuentas encontradas: " + accounts + "."
                + (assigned.isEmpty() ? " Asigna cada una a su marca en la ficha." : " Asignadas solas: " + String.join(", ", assigned) + ".");
    }

    /** Asigna a cada marca sin cuenta la que coincide con su @usuario. Devuelve «Marca → @usuario». */
    private List<String> autoAssign(List<MetaOAuthGateway.PageAccount> found) {
        List<String> assigned = new ArrayList<>();
        for (var brand : promotions.brandRefs()) {
            String handle = normalize(promotions.handleOf(brand.id()));
            if (handle.isEmpty()) continue;
            var match = found.stream().filter(a -> normalize(a.username()).equals(handle)).findFirst().orElse(null);
            if (match == null) continue;
            var current = accounts.account(brand.id()).orElse(null);
            // No se pisa una cuenta que Raúl ya puso a mano y es otra
            if (current != null && !current.igUserId().equals(match.igUserId())) continue;
            service.assignDetected(brand.id(), match.igUserId(), match.username());
            assigned.add(brand.name() + " → @" + match.username());
        }
        return assigned;
    }

    static String normalize(@Nullable String handle) {
        if (handle == null) return "";
        String h = handle.strip().toLowerCase(Locale.ROOT);
        return h.startsWith("@") ? h.substring(1) : h;
    }

    // ---------------------------------------------------------------- Cuentas y desconexión

    /** Las cuentas de Instagram de las páginas de Raúl (llama a Meta), con la marca a la que está asignada cada una. */
    @Override
    public List<Detected> detected() {
        String key = credentials.activeToken().orElseThrow(() -> new IllegalArgumentException(
                "Primero conecta con Instagram o pega la clave de usuario del sistema"));
        List<MetaOAuthGateway.PageAccount> pages;
        try {
            pages = oauth.pages(key);
        } catch (InstagramException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        List<Detected> out = new ArrayList<>();
        var brands = promotions.brandRefs();
        for (var page : pages) {
            if (!page.hasInstagram()) continue;
            String brandId = brands.stream().filter(b -> accounts.account(b.id())
                    .map(a -> a.igUserId().equals(page.igUserId())).orElse(false)).map(PromotionQueries.BrandRef::id).findFirst().orElse(null);
            out.add(new Detected(page.igUserId(), page.username(), page.pageName(), brandId));
        }
        return out;
    }

    /** Borra la conexión (no toca la clave de usuario del sistema ni las cuentas de las marcas). */
    @Override
    public void disconnect() {
        credentials.deleteConnection();
        service.refresh();
    }

    // ---------------------------------------------------------------- Aviso de caducidad

    /** La conexión caduca en {@value #WARN_DAYS} días o menos (o ya caducó). */
    @Override
    public Optional<Notice> notice() {
        Instant now = clock.instant();
        return credentials.connection().filter(c -> c.expired(now) || c.daysLeft(now) <= WARN_DAYS)
                .map(c -> new Notice(c.expired(now), c.daysLeft(now), credentials.systemKey().isPresent()));
    }
}
