package com.socialhub.publishing.adapter.out.instagram;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.MetaOAuthGateway;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inicio de sesión de Meta con el {@link HttpClient} de Java, sin librerías nuevas. El App Secret y las claves viajan
 * siempre en el cuerpo de un POST o en la cabecera {@code Authorization}, nunca en la URL, y no se escriben en los
 * registros ni en «Operaciones externas» (el detalle de cada llamada no los lleva).
 */
@Component
class HttpMetaOAuthGateway implements MetaOAuthGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HttpMetaOAuthGateway.class);
    private static final String SYSTEM = HttpInstagramGateway.SYSTEM;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PAGES = 10;

    private final String graphUrl;
    private final String dialogUrl;
    private final String version;
    private final ExternalCalls calls;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    HttpMetaOAuthGateway(@Value("${socialhub.instagram.graph-url:https://graph.facebook.com}") String graphUrl,
                         @Value("${socialhub.instagram.dialog-url:https://www.facebook.com}") String dialogUrl,
                         @Value("${socialhub.instagram.version:v23.0}") String version, ExternalCalls calls) {
        this.graphUrl = trim(graphUrl);
        this.dialogUrl = trim(dialogUrl);
        this.version = version;
        this.calls = calls;
    }

    @Override
    public String authorizationUrl(String appId, String redirectUri, String state, List<String> scopes) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", appId);
        query.put("redirect_uri", redirectUri);
        query.put("state", state);
        query.put("response_type", "code");
        query.put("scope", String.join(",", scopes));
        return dialogUrl + "/" + version + "/dialog/oauth?" + form(query);
    }

    @Override
    public void verifyApp(String appId, String appSecret) {
        calls.run(SYSTEM, "oauth_verify_app", null, () -> post("/oauth/access_token",
                Map.of("grant_type", "client_credentials", "client_id", appId, "client_secret", appSecret)));
    }

    @Override
    public Token exchangeCode(String appId, String appSecret, String redirectUri, String code) {
        JsonNode body = calls.call(SYSTEM, "oauth_code", null, () -> post("/oauth/access_token",
                Map.of("client_id", appId, "client_secret", appSecret, "redirect_uri", redirectUri, "code", code)));
        return token(body);
    }

    @Override
    public Token toLongLived(String appId, String appSecret, String shortLived) {
        JsonNode body = calls.call(SYSTEM, "oauth_long_lived", null, () -> post("/oauth/access_token",
                Map.of("grant_type", "fb_exchange_token", "client_id", appId, "client_secret", appSecret,
                        "fb_exchange_token", shortLived)));
        return token(body);
    }

    @Override
    public List<PageAccount> pages(String token) {
        List<PageAccount> pages = new ArrayList<>();
        String after = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            String cursor = after;
            JsonNode body = calls.call(SYSTEM, "me_accounts", null, () -> get(token, "/me/accounts", cursor));
            for (JsonNode page : body.path("data")) {
                JsonNode ig = page.path("instagram_business_account");
                pages.add(new PageAccount(text(page.path("name")) == null ? "(sin nombre)" : text(page.path("name")),
                        text(ig.path("id")), text(ig.path("username"))));
            }
            after = text(body.path("paging").path("cursors").path("after"));
            if (after == null || body.path("paging").path("next").isMissingNode()) break;
        }
        return pages;
    }

    // ---------------------------------------------------------------- HTTP

    private Token token(JsonNode body) {
        String value = text(body.path("access_token"));
        if (value == null) throw new InstagramException(InstagramException.Kind.REJECTED, "Meta no devolvió la clave");
        JsonNode expires = body.path("expires_in");
        return new Token(value, expires.isNumber() ? expires.asLong() : null);
    }

    private JsonNode get(String token, String path, @Nullable String after) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("locale", "es_ES");
        query.put("fields", "name,instagram_business_account{id,username}");
        query.put("limit", "100");
        if (after != null) query.put("after", after);
        return send(HttpRequest.newBuilder(URI.create(graphUrl + "/" + version + path + "?" + form(query)))
                .timeout(TIMEOUT).header("Authorization", "OAuth " + token).header("Accept", "application/json").GET().build());
    }

    private JsonNode post(String path, Map<String, String> fields) {
        return send(HttpRequest.newBuilder(URI.create(graphUrl + "/" + version + path)).timeout(TIMEOUT)
                .header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build());
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = parse(response.body());
            check(response.statusCode(), body);
            return body;
        } catch (IOException e) {
            throw new InstagramException(InstagramException.Kind.UNCERTAIN, "No se pudo conectar con Meta: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InstagramException(InstagramException.Kind.UNCERTAIN, "Llamada a Meta interrumpida");
        }
    }

    private static void check(int code, JsonNode body) {
        if (code / 100 == 2) return;
        JsonNode error = body.path("error");
        String original = text(error.path("message"));
        int errorCode = error.path("code").asInt(0);
        int subcode = error.path("error_subcode").asInt(0);
        // El texto original de Meta solo va al registro (no lleva claves); a Raúl se le da la traducción
        log.warn("Meta respondió {} en el inicio de sesión (código {}, subcódigo {}): {}", code, errorCode, subcode, original);
        String lower = original == null ? "" : original.toLowerCase(Locale.ROOT);
        String message;
        if (lower.contains("client secret")) {
            message = "Meta no acepta el App Secret: cópialo de nuevo en tu app › Configuración › Básica";
        } else if (lower.contains("redirect_uri") || lower.contains("redirect uri")) {
            message = "la dirección de redireccionamiento no coincide con la de tu app en Meta: copia la de SocialHub en "
                    + "«URI de redireccionamiento OAuth válidas»";
        } else if (errorCode == 101 || lower.contains("app id") || lower.contains("application")) {
            message = "Meta no reconoce el App ID o la app no está disponible: revísalo en tu app › Configuración › Básica";
        } else if (lower.contains("authorization code") || lower.contains("verification code")) {
            message = "Meta no acepta el código de autorización (ya se usó o ha caducado): vuelve a pulsar «Conectar con Instagram»";
        } else if (errorCode == 190) {
            message = "Meta no acepta la clave: está caducada o revocada";
        } else {
            message = MetaErrors.translate(code, errorCode, subcode);
        }
        throw new InstagramException(code / 100 == 5 ? InstagramException.Kind.UNCERTAIN : InstagramException.Kind.REJECTED, message);
    }

    private JsonNode parse(@Nullable String body) {
        if (body == null || body.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    private static @Nullable String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String s = node.asString("");
        return s.isBlank() ? null : s;
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
    }

    private static String trim(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }

    private static String enc(String part) { return URLEncoder.encode(part, StandardCharsets.UTF_8); }
}
