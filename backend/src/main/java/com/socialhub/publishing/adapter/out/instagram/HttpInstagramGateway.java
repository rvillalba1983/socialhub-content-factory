package com.socialhub.publishing.adapter.out.instagram;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.publishing.domain.InstagramGateway;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la Meta Graph API (inicio de sesión unificado) con el {@link HttpClient} de Java, sin librerías nuevas.
 * Versión fija y configurable. La clave va en la cabecera {@code Authorization} (nunca en la URL) y no se escribe en
 * los registros. Cada llamada queda en «Operaciones externas» con el sistema «Instagram».
 */
@Component
class HttpInstagramGateway implements InstagramGateway {

    static final String SYSTEM = "Instagram";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(15);

    private final String graphUrl;
    private final String uploadUrl;
    private final String version;
    private final ExternalCalls calls;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    HttpInstagramGateway(@Value("${socialhub.instagram.graph-url:https://graph.facebook.com}") String graphUrl,
                         @Value("${socialhub.instagram.upload-url:https://rupload.facebook.com}") String uploadUrl,
                         @Value("${socialhub.instagram.version:v23.0}") String version, ExternalCalls calls) {
        this.graphUrl = trim(graphUrl);
        this.uploadUrl = trim(uploadUrl);
        this.version = version;
        this.calls = calls;
    }

    @Override
    public Identity me(String key) {
        JsonNode body = calls.call(SYSTEM, "me", null, () -> get(key, "/me", "id,name"));
        return new Identity(body.path("id").asString(""), text(body.path("name")));
    }

    @Override
    public Account account(String key, String igUserId) {
        JsonNode body = calls.call(SYSTEM, "account", igUserId, () -> get(key, "/" + enc(igUserId), "id,username"));
        String username = text(body.path("username"));
        if (username == null) {
            throw new InstagramException(InstagramException.Kind.REJECTED, "Meta no devolvió el @usuario de la cuenta " + igUserId);
        }
        return new Account(body.path("id").asString(igUserId), username);
    }

    @Override
    public Quota publishingLimit(String key, String igUserId) {
        JsonNode body = calls.call(SYSTEM, "content_publishing_limit", igUserId,
                () -> get(key, "/" + enc(igUserId) + "/content_publishing_limit", "quota_usage,config"));
        JsonNode first = body.path("data").path(0);
        int used = first.path("quota_usage").asInt(0);
        int total = first.path("config").path("quota_total").asInt(100);
        return new Quota(used, total);
    }

    @Override
    public String createContainer(String key, String igUserId, Map<String, String> params) {
        JsonNode body = calls.call(SYSTEM, "media", igUserId, () -> post(key, "/" + enc(igUserId) + "/media", params));
        return id(body, "el contenedor");
    }

    @Override
    public void upload(String key, String containerId, Path file) {
        calls.run(SYSTEM, "upload", containerId, () -> {
            try {
                long size = Files.size(file);
                HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl + "/ig-api-upload/" + version + "/" + enc(containerId)))
                        .timeout(UPLOAD_TIMEOUT)
                        .header("Authorization", "OAuth " + key)
                        .header("offset", "0")
                        .header("file_size", Long.toString(size))
                        .POST(HttpRequest.BodyPublishers.ofFile(file)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode body = parse(response.body());
                check(response.statusCode(), body);
                if (body.has("success") && !body.path("success").asBoolean(true)) {
                    throw new InstagramException(InstagramException.Kind.REJECTED,
                            "Meta no aceptó el vídeo: " + text(body.path("message")));
                }
            } catch (FileNotFoundException | java.nio.file.NoSuchFileException e) {
                throw new InstagramException(InstagramException.Kind.REJECTED, "No se encuentra el archivo " + file);
            } catch (IOException e) {
                throw new InstagramException(InstagramException.Kind.UNCERTAIN, "No se pudo subir el vídeo a Meta: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InstagramException(InstagramException.Kind.UNCERTAIN, "Subida a Meta interrumpida");
            }
        });
    }

    @Override
    public Container container(String key, String containerId) {
        JsonNode body = calls.call(SYSTEM, "container", containerId, () -> get(key, "/" + enc(containerId), "status_code,status"));
        String code = body.path("status_code").asString("");
        ContainerState state;
        try {
            state = ContainerState.valueOf(code);
        } catch (IllegalArgumentException e) {
            return new Container(ContainerState.ERROR, "estado desconocido «" + code + "»");
        }
        return new Container(state, text(body.path("status")));
    }

    @Override
    public String publish(String key, String igUserId, String containerId) {
        JsonNode body = calls.call(SYSTEM, "media_publish", igUserId,
                () -> post(key, "/" + enc(igUserId) + "/media_publish", Map.of("creation_id", containerId)));
        String id = text(body.path("id"));
        // Respuesta 200 sin identificador: no se sabe si se publicó
        if (id == null) throw new InstagramException(InstagramException.Kind.UNCERTAIN, "Meta respondió sin el identificador de la publicación");
        return id;
    }

    @Override
    public @Nullable String permalink(String key, String mediaId) {
        JsonNode body = calls.call(SYSTEM, "permalink", mediaId, () -> get(key, "/" + enc(mediaId), "permalink"));
        return text(body.path("permalink"));
    }

    // ---------------------------------------------------------------- HTTP

    private JsonNode get(String key, String path, String fields) {
        return send(HttpRequest.newBuilder(URI.create(graphUrl + "/" + version + path + "?fields=" + enc(fields)))
                .timeout(TIMEOUT).header("Authorization", "OAuth " + key).header("Accept", "application/json").GET().build());
    }

    private JsonNode post(String key, String path, Map<String, String> form) {
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(graphUrl + "/" + version + path)).timeout(TIMEOUT)
                .header("Authorization", "OAuth " + key).header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
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
        String message = text(error.path("error_user_msg"));
        if (message == null) message = text(error.path("message"));
        int errorCode = error.path("code").asInt(0);
        if (code == 401 || errorCode == 190) {
            throw new InstagramException(InstagramException.Kind.INVALID_KEY,
                    "Meta no acepta la clave" + (message == null ? "" : ": " + message));
        }
        String text = "Meta respondió " + code + (errorCode == 0 ? "" : " (código " + errorCode + ")")
                + (message == null ? "" : ": " + message);
        throw new InstagramException(code / 100 == 5 ? InstagramException.Kind.UNCERTAIN : InstagramException.Kind.REJECTED, text);
    }

    private static String id(JsonNode body, String what) {
        String id = text(body.path("id"));
        if (id == null) throw new InstagramException(InstagramException.Kind.REJECTED, "Meta no devolvió el identificador de " + what);
        return id;
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

    private static String trim(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }

    private static String enc(String part) { return URLEncoder.encode(part, StandardCharsets.UTF_8); }
}
