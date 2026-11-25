package com.socialhub.publishing.adapter.out.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.publishing.domain.InstagramGateway;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Búsqueda de música (Instagram Audio API) contra un Meta de mentira (servidor local; nunca se llama a Meta). */
class HttpInstagramAudioTest {

    HttpServer meta;
    HttpInstagramGateway gateway;
    final List<String> requests = new CopyOnWriteArrayList<>();
    volatile int code = 200;
    volatile String reply = "";

    @BeforeEach
    void start() throws IOException {
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", ex -> {
            requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + "?" + ex.getRequestURI().getRawQuery()
                    + " | " + ex.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        meta.start();
        gateway = new HttpInstagramGateway("http://127.0.0.1:" + meta.getAddress().getPort(), "http://127.0.0.1:1", "v23.0",
                ExternalCalls.unobserved());
    }

    @AfterEach
    void stop() { meta.stop(0); }

    @Test
    void searchesMusicWithTheKeyInTheHeaderAndReadsTheSounds() {
        reply = "{\"audio\":[{\"audio_id\":\"587784541076604\",\"display_artist\":\"Litoques\",\"duration_in_ms\":153760,"
                + "\"audio_type\":\"music\",\"title\":\"Púa\",\"download_url\":\"https://x\"},"
                + "{\"audio_id\":\"2\",\"title\":\"Sin artista\"},{\"title\":\"Sin id\"}]}";
        List<InstagramGateway.Audio> found = gateway.searchMusic("CLAVE", "1784", "Púa y más");
        assertThat(found).containsExactly(new InstagramGateway.Audio("587784541076604", "Púa", "Litoques"),
                new InstagramGateway.Audio("2", "Sin artista", null));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst()).startsWith("GET /v23.0/ig_audio?").contains("audio_type=music").contains("user_id=1784")
                .contains("search_query=P%C3%BAa+y+m%C3%A1s").endsWith("| OAuth CLAVE").doesNotContain("access_token");
    }

    @Test
    void noResultsIsAnEmptyListAndMetaErrorsAreTranslated() {
        reply = "{\"audio\":[]}";
        assertThat(gateway.searchMusic("CLAVE", "1784", "nada")).isEmpty();
        code = 400;
        reply = "{\"error\":{\"message\":\"(#10) Application does not have permission for this action\",\"code\":10}}";
        assertThatThrownBy(() -> gateway.searchMusic("CLAVE", "1784", "Púa")).isInstanceOf(InstagramGateway.InstagramException.class)
                .extracting(e -> ((InstagramGateway.InstagramException) e).kind()).isEqualTo(InstagramGateway.InstagramException.Kind.REJECTED);
    }
}
