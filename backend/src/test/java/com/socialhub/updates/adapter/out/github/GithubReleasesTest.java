package com.socialhub.updates.adapter.out.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.updates.domain.ReleaseSource;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lectura de las releases contra un GitHub de mentira: sin datos del usuario y robusta ante lo raro. */
class GithubReleasesTest {

    HttpServer github;
    volatile int code = 200;
    volatile String body = "[]";
    final List<String> headers = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/", ex -> {
            ex.getRequestHeaders().forEach((k, v) -> headers.add(k.toLowerCase() + "=" + v));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        github.start();
    }

    @AfterEach
    void stop() { github.stop(0); }

    private GithubReleases source() {
        return new GithubReleases("http://127.0.0.1:" + github.getAddress().getPort() + "/releases", ExternalCalls.unobserved());
    }

    @Test
    void readsPublishedAndPreReleasesButNotDraftsOrOddTags() {
        body = """
                [{"tag_name":"v1.3.0","draft":false,"prerelease":true,"html_url":"https://github.com/x/y/releases/tag/v1.3.0",
                  "assets":[{"name":"SocialHub-1.3.0.msi","browser_download_url":"https://github.com/x/y/releases/download/v1.3.0/SocialHub-1.3.0.msi"},
                            {"name":"raro.msi","browser_download_url":"http://inseguro/x.msi"}]},
                 {"tag_name":"v9.9.9","draft":true,"html_url":"https://github.com/x/y/releases/tag/v9.9.9","assets":[]},
                 {"tag_name":"nightly","draft":false,"html_url":"https://github.com/x/y/releases/tag/nightly","assets":[]}]""";
        var releases = source().releases();
        assertThat(releases).hasSize(1);
        assertThat(releases.getFirst().version()).isEqualTo("1.3.0");
        assertThat(releases.getFirst().assets()).extracting(a -> a.name()).containsExactly("SocialHub-1.3.0.msi");
        assertThat(releases.getFirst().installerFor("Windows 11")).isPresent();
    }

    @Test
    void sendsNothingAboutTheUser() {
        source().releases();
        assertThat(headers).noneMatch(h -> h.startsWith("authorization=") || h.startsWith("cookie="));
        assertThat(headers).anyMatch(h -> h.startsWith("user-agent=") && h.contains("SocialHub"));
    }

    @Test
    void noReleasesAndRepositoryNotFoundAreAnEmptyList() {
        assertThat(source().releases()).isEmpty();
        code = 404;
        body = "{\"message\":\"Not Found\"}";
        assertThat(source().releases()).isEmpty();
    }

    @Test
    void errorsAndGarbageAreReportedAsUnavailable() {
        code = 403;
        assertThatThrownBy(() -> source().releases()).isInstanceOf(ReleaseSource.Unavailable.class);
        code = 200;
        body = "<html>no es json</html>";
        assertThatThrownBy(() -> source().releases()).isInstanceOf(ReleaseSource.Unavailable.class);
        github.stop(0);
        assertThatThrownBy(() -> source().releases()).isInstanceOf(ReleaseSource.Unavailable.class);
    }
}
