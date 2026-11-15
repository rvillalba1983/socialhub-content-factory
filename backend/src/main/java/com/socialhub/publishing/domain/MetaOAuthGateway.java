package com.socialhub.publishing.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Inicio de sesión de Meta (OAuth 2.0 con Facebook Login) para conseguir la clave de la API oficial de Instagram sin
 * crear a mano un usuario del sistema. Los fallos salen como {@link InstagramGateway.InstagramException}. Ni el App
 * Secret ni las claves se escriben nunca en los registros.
 */
@SecondaryPort
public interface MetaOAuthGateway {

    /** Clave de Meta y, si la devuelve, lo que dura en segundos. */
    record Token(String value, @Nullable Long expiresInSeconds) {}

    /** Página de Facebook con la cuenta de Instagram profesional que tiene vinculada (si la tiene). */
    record PageAccount(String pageName, @Nullable String igUserId, @Nullable String username) {
        public boolean hasInstagram() { return igUserId != null && username != null; }
    }

    /** Dirección de Meta a la que se manda a Raúl para que dé su permiso. */
    String authorizationUrl(String appId, String redirectUri, String state, List<String> scopes);

    /** Comprueba el par App ID y App Secret con Meta, sin cambiar nada. */
    void verifyApp(String appId, String appSecret);

    /** Cambia el {@code code} de la vuelta del inicio de sesión por una clave de usuario de corta duración. */
    Token exchangeCode(String appId, String appSecret, String redirectUri, String code);

    /** Cambia la clave de corta duración por una de larga duración (unos 60 días). */
    Token toLongLived(String appId, String appSecret, String shortLived);

    /** GET /me/accounts?fields=name,instagram_business_account{id,username}: todas las páginas, sea cual sea su paginación. */
    List<PageAccount> pages(String token);
}
