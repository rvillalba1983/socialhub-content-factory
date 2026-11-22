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

/** Lectura de las métricas de una publicación contra un Meta de mentira (servidor local; nunca se llama a Meta). */
class HttpInstagramInsightsTest {

    HttpServer meta;
    HttpInstagramGateway gateway;
    final List<String> queries = new CopyOnWriteArrayList<>();
    volatile boolean noPermission = false;
    /** Métricas que el «tipo de publicación» no admite: si se piden juntas, Meta rechaza toda la petición. */
    volatile String unsupported = "";

    @BeforeEach
    void start() throws IOException {
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String query = String.valueOf(ex.getRequestURI().getQuery());
            queries.add(path + "?" + query);
            int code = 200;
            String reply;
            if (noPermission) {
                code = 400;
                reply = "{\"error\":{\"message\":\"(#10) Application does not have permission for this action\",\"code\":10}}";
            } else if (path.endsWith("/insights")) {
                String asked = query.substring(query.indexOf("metric=") + 7).split("&")[0].replace("%2C", ",");
                if (!unsupported.isEmpty() && asked.contains(unsupported)) {
                    code = 400;
                    reply = "{\"error\":{\"message\":\"Invalid parameter\",\"code\":100,\"error_subcode\":2108006}}";
                } else {
                    StringBuilder data = new StringBuilder();
                    for (String metric : asked.split(",")) {
                        if (data.length() > 0) data.append(',');
                        data.append("{\"name\":\"").append(metric).append("\",\"values\":[{\"value\":")
                                .append(metric.equals("views") ? 1200 : metric.equals("reach") ? 800 : metric.equals("saved") ? 9 : 3).append("}]}");
                    }
                    reply = "{\"data\":[" + data + "]}";
                }
            } else {
                reply = "{\"like_count\":64,\"id\":\"m-1\"}";
            }
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
    void readsTheMetricsMetaGivesAndLeavesTheRestNull() {
        var m = gateway.mediaInsights("k", "m-1");
        assertThat(m.views()).isEqualTo(1200);
        assertThat(m.reach()).isEqualTo(800);
        assertThat(m.saves()).isEqualTo(9);
        assertThat(m.shares()).isEqualTo(3);
        assertThat(m.likes()).isEqualTo(64);
        assertThat(m.comments()).as("Meta no devolvió comments_count").isNull();
        assertThat(queries.getFirst()).contains("/v23.0/m-1/insights").contains("views");
    }

    @Test
    void ifOneMetricIsNotSupportedTheOthersAreStillRead() {
        unsupported = "shares";
        var m = gateway.mediaInsights("k", "m-1");
        assertThat(m.shares()).isNull();
        assertThat(m.views()).isEqualTo(1200);
        assertThat(m.saves()).isEqualTo(9);
    }

    @Test
    void withoutPermissionItFailsWithTheReasonSoTheCallerCanFallBack() {
        noPermission = true;
        assertThatThrownBy(() -> gateway.mediaInsights("k", "m-1")).isInstanceOf(InstagramGateway.InstagramException.class);
    }
}
