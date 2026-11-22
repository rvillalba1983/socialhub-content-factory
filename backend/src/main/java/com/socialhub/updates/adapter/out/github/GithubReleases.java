package com.socialhub.updates.adapter.out.github;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.updates.domain.Release;
import com.socialhub.updates.domain.ReleaseSource;
import com.socialhub.updates.domain.Versions;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las releases del repositorio por la API pública de GitHub: una sola petición GET, sin credenciales, sin cuerpo y sin
 * ningún dato del usuario (solo la cabecera User-Agent genérica que GitHub exige). Incluye las «pre-release», que es como
 * las publica hoy el workflow de instaladores.
 */
@Component
class GithubReleases implements ReleaseSource {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String url;
    private final ExternalCalls calls;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).proxy(ProxySelector.getDefault()).build();
    private final JsonMapper json = JsonMapper.builder().build();

    GithubReleases(@Value("${socialhub.updates.releases-url:https://api.github.com/repos/rvillalba1983/socialhub-content-factory/releases?per_page=30}") String url,
                   ExternalCalls calls) {
        this.url = url;
        this.calls = calls;
    }

    @Override
    public List<Release> releases() {
        return calls.call("GitHub", "releases", null, this::fetch);
    }

    private List<Release> fetch() {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "SocialHub").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return List.of();
            if (response.statusCode() / 100 != 2) throw new Unavailable("GitHub respondió " + response.statusCode());
            JsonNode body = json.readTree(response.body());
            if (!body.isArray()) throw new Unavailable("GitHub devolvió algo que no es una lista");
            List<Release> out = new ArrayList<>();
            for (JsonNode r : body) {
                if (r.path("draft").asBoolean(false)) continue;
                String version = Versions.clean(r.path("tag_name").asString(""));
                if (Versions.parse(version).isEmpty()) continue;
                List<Release.Asset> assets = new ArrayList<>();
                for (JsonNode a : r.path("assets")) {
                    String name = a.path("name").asString("");
                    String link = a.path("browser_download_url").asString("");
                    if (!name.isEmpty() && link.startsWith("https://")) assets.add(new Release.Asset(name, link));
                }
                String page = r.path("html_url").asString("");
                if (!page.startsWith("https://")) continue;
                out.add(new Release(version, page, List.copyOf(assets)));
            }
            return out;
        } catch (IOException e) {
            throw new Unavailable("sin conexión con GitHub: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("comprobación interrumpida");
        } catch (JacksonException | IllegalArgumentException e) {
            throw new Unavailable("respuesta de GitHub no válida");
        }
    }
}
