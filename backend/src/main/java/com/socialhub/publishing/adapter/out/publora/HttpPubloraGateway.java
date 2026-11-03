package com.socialhub.publishing.adapter.out.publora;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.publishing.domain.PubloraGateway;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la API REST de Publora con el {@link HttpClient} de Java (sin librerías nuevas). Cada llamada queda en
 * «Operaciones externas» con el sistema «Publora». La clave va en la cabecera {@code x-publora-key} y nunca se
 * escribe en los registros.
 */
@Slf4j
@Component
class HttpPubloraGateway implements PubloraGateway {

    static final String SYSTEM = "Publora";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final String baseUrl;
    private final ExternalCalls calls;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final JsonMapper json = JsonMapper.builder().build();

    HttpPubloraGateway(@Value("${socialhub.publora.base-url:https://api.publora.com/api/v1}") String baseUrl, ExternalCalls calls) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.calls = calls;
    }

    @Override
    public List<Connection> connections(String key) {
        JsonNode body = calls.call(SYSTEM, "platform-connections", null, () -> send(key, "GET", "/platform-connections", null, null));
        List<Connection> list = new ArrayList<>();
        for (JsonNode c : body.path("connections")) {
            String id = c.path("platformId").asString("");
            if (id.isEmpty()) continue;
            int dash = id.indexOf('-');
            String platform = dash > 0 ? id.substring(0, dash) : id;
            boolean usable = !"reconnect_required".equalsIgnoreCase(text(c.path("connectionStatus")));
            list.add(new Connection(id, platform, text(c.path("username")), text(c.path("displayName")), usable));
        }
        return list;
    }

    @Override
    public String schedule(String key, String connectionId, String content, Map<String, Object> platformSettings,
                           List<Media> media, Instant at, String idempotencyKey) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("content", content);
        draft.put("platforms", List.of(connectionId));
        if (!platformSettings.isEmpty()) draft.put("platformSettings", platformSettings);
        JsonNode created = calls.call(SYSTEM, "create-post", connectionId,
                () -> send(key, "POST", "/create-post", draft, idempotencyKey));
        String group = created.path("postGroupId").asString("");
        if (group.isEmpty()) throw new PubloraException("Publora no devolvió el identificador de la publicación");
        try {
            for (Media m : media) upload(key, group, m);
            Map<String, Object> schedule = new LinkedHashMap<>();
            schedule.put("status", "scheduled");
            schedule.put("scheduledTime", at.toString());
            calls.call(SYSTEM, "update-post", group, () -> send(key, "PUT", "/update-post/" + enc(group), schedule, null));
            return group;
        } catch (RuntimeException e) {
            try {
                delete(key, group);
            } catch (RuntimeException cleanup) {
                log.warn("Publora: no se pudo borrar el borrador {} tras el error: {}", group, cleanup.getMessage());
            }
            throw e;
        }
    }

    private void upload(String key, String group, Media m) {
        String name = m.file().getFileName().toString();
        Map<String, Object> ask = Map.of("fileName", name, "contentType", m.contentType(),
                "type", m.video() ? "video" : "image", "postGroupId", group);
        JsonNode url = calls.call(SYSTEM, "get-upload-url", name, () -> send(key, "POST", "/get-upload-url", ask, null));
        String uploadUrl = url.path("uploadUrl").asString("");
        String mediaId = url.path("mediaId").asString("");
        if (uploadUrl.isEmpty() || mediaId.isEmpty()) throw new PubloraException("Publora no dio dónde subir " + name);
        calls.run(SYSTEM, "upload", name, () -> {
            try {
                HttpRequest put = HttpRequest.newBuilder(URI.create(uploadUrl)).timeout(UPLOAD_TIMEOUT)
                        .header("Content-Type", m.contentType()).PUT(HttpRequest.BodyPublishers.ofFile(m.file())).build();
                HttpResponse<String> response = http.send(put, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new PubloraException("No se pudo subir " + name + " a Publora (HTTP " + response.statusCode() + ")");
                }
            } catch (FileNotFoundException e) {
                throw new PubloraException("No se encuentra el archivo " + m.file());
            } catch (IOException e) {
                throw new PubloraException("No se pudo subir " + name + " a Publora: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PubloraException("Subida interrumpida");
            }
        });
        calls.call(SYSTEM, "complete-media", name, () -> send(key, "POST", "/complete-media/" + enc(mediaId), Map.of(), null));
    }

    @Override
    public List<Listed> list(String key, Instant from, Instant to) {
        List<Listed> list = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            String query = "/list-posts?page=" + page + "&limit=100&sortBy=scheduledTime&sortOrder=asc&fromDate="
                    + enc(from.toString()) + "&toDate=" + enc(to.toString());
            JsonNode body = calls.call(SYSTEM, "list-posts", "página " + page, () -> send(key, "GET", query, null, null));
            JsonNode posts = body.path("posts");
            for (JsonNode p : posts) {
                String when = text(p.path("scheduledTime"));
                if (when == null) continue;
                Instant at;
                try {
                    at = Instant.parse(when);
                } catch (java.time.format.DateTimeParseException e) {
                    continue;
                }
                if (at.isBefore(from) || !at.isBefore(to)) continue;
                for (JsonNode target : p.path("platforms")) {
                    String id = text(target.path("platformId"));
                    String platform = text(target.path("platform"));
                    if (id == null) continue;
                    String connection = platform != null && !id.startsWith(platform + "-") ? platform + "-" + id : id;
                    String status = text(target.path("status"));
                    list.add(new Listed(p.path("postGroupId").asString(""), connection, at,
                            status != null ? status : p.path("status").asString(""), text(p.path("content"))));
                }
            }
            if (posts.size() < 100) break;
        }
        return list;
    }

    @Override
    public PostState post(String key, String postGroupId) {
        JsonNode body = calls.call(SYSTEM, "get-post", postGroupId, () -> send(key, "GET", "/get-post/" + enc(postGroupId), null, null));
        String status = body.path("status").asString("");
        JsonNode first = body.path("posts").path(0);
        String error = text(first.path("error"));
        if (error == null) error = text(first.path("errorMessage"));
        return new PostState(status, text(first.path("postedId")), text(first.path("permalink")), error);
    }

    @Override
    public void delete(String key, String postGroupId) {
        calls.call(SYSTEM, "delete-post", postGroupId, () -> send(key, "DELETE", "/delete-post/" + enc(postGroupId), null, null));
    }

    // ---------------------------------------------------------------- HTTP

    private JsonNode send(String key, String method, String path, @Nullable Object body, @Nullable String idempotencyKey) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(TIMEOUT)
                    .header("x-publora-key", key)
                    .header("x-publora-client", "socialhub")
                    .header("Accept", "application/json");
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = parse(response.body());
            int code = response.statusCode();
            if (code == 401) throw new PubloraException("Publora no acepta la clave de la API");
            if (code / 100 != 2) {
                String error = text(node.path("error"));
                String reason = text(node.path("code"));
                throw new PubloraException("Publora respondió " + code
                        + (reason == null ? "" : " (" + reason + ")") + (error == null ? "" : ": " + error));
            }
            return node;
        } catch (IOException e) {
            throw new PubloraException("No se pudo conectar con Publora: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PubloraException("Llamada a Publora interrumpida");
        }
    }

    private JsonNode parse(String body) {
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

    private static String enc(String part) { return URLEncoder.encode(part, StandardCharsets.UTF_8); }
}
