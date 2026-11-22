package com.socialhub.publishing.adapter.out.links;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.publishing.domain.LinkProbe.State;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Con un servidor local (las pruebas permiten http y 127.0.0.1; lo demás privado sigue prohibido). */
class HttpLinkProbeTest {

    private HttpServer server;
    private String base;
    private final List<String> requestHeaders = new CopyOnWriteArrayList<>();
    private final AtomicInteger flaky = new AtomicInteger();
    private final HttpLinkProbe probe = new HttpLinkProbe(HttpLinkProbe.Policy.loopbackOverHttp());

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> {
            ex.getRequestHeaders().forEach((k, v) -> requestHeaders.add(k.toLowerCase() + "=" + String.join(",", v)));
            ex.sendResponseHeaders(200, 0);
            try (var out = ex.getResponseBody()) { out.write(new byte[200_000]); } catch (java.io.IOException ignored) { /* corta el cliente */ }
        });
        server.createContext("/missing", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        server.createContext("/redirect", ex -> { ex.getResponseHeaders().add("Location", "/ok"); ex.sendResponseHeaders(302, -1); ex.close(); });
        server.createContext("/loop", ex -> { ex.getResponseHeaders().add("Location", "/loop"); ex.sendResponseHeaders(302, -1); ex.close(); });
        server.createContext("/to-metadata", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            ex.sendResponseHeaders(302, -1); ex.close();
        });
        server.createContext("/to-private", ex -> {
            ex.getResponseHeaders().add("Location", "http://10.0.0.5/admin");
            ex.sendResponseHeaders(301, -1); ex.close();
        });
        server.createContext("/flaky", ex -> {
            ex.sendResponseHeaders(flaky.getAndIncrement() == 0 ? 503 : 200, -1); ex.close();
        });
        server.createContext("/slow", ex -> {
            try { Thread.sleep(9000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void aLinkThatAnswers2xxIsOkEvenWhenTheBodyIsHuge() {
        assertThat(probe.probe(base + "/ok")).isEqualTo(State.OK);
    }

    @Test
    void followsRedirects() {
        assertThat(probe.probe(base + "/redirect")).isEqualTo(State.OK);
    }

    @Test
    void anErrorStatusOrAnEndlessRedirectIsNotResponding() {
        assertThat(probe.probe(base + "/missing")).isEqualTo(State.NOT_RESPONDING);
        assertThat(probe.probe(base + "/loop")).isEqualTo(State.NOT_RESPONDING);
    }

    @Test
    void retriesOnceBeforeGivingUp() {
        assertThat(probe.probe(base + "/flaky")).isEqualTo(State.OK);
        assertThat(flaky.get()).isEqualTo(2);
    }

    @Test
    void aSlowServerIsNotRespondingWithinAShortTime() {
        long start = System.nanoTime();
        assertThat(probe.probe(base + "/slow")).isEqualTo(State.NOT_RESPONDING);
        assertThat((System.nanoTime() - start) / 1_000_000_000).as("seconds").isLessThan(20);
    }

    @Test
    void redirectsToPrivateOrMetadataAddressesAreRefusedAfterTheRedirect() {
        assertThat(probe.probe(base + "/to-metadata")).isEqualTo(State.REFUSED);
        assertThat(probe.probe(base + "/to-private")).isEqualTo(State.REFUSED);
    }

    @Test
    void sendsNoCookiesNoAuthorizationAndANeutralUserAgent() {
        probe.probe(base + "/ok");
        assertThat(requestHeaders).anyMatch(h -> h.startsWith("user-agent=LinkCheck"));
        assertThat(requestHeaders).noneMatch(h -> h.startsWith("cookie=") || h.startsWith("authorization="));
    }

    @Test
    void theStrictPolicyOnlyAllowsHttpsToPublicAddresses() {
        HttpLinkProbe strict = new HttpLinkProbe();
        assertThat(strict.probe(base + "/ok")).as("http").isEqualTo(State.REFUSED);
        assertThat(strict.probe("https://127.0.0.1/ok")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("https://localhost/ok")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("https://169.254.169.254/latest/meta-data/")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("https://user:pass@example.com/")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("https://[::1]/")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("ftp://example.com/")).isEqualTo(State.REFUSED);
        assertThat(strict.probe("no es un enlace")).isEqualTo(State.REFUSED);
    }

    @Test
    void classifiesNonPublicAddresses() throws Exception {
        for (String a : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::")) {
            assertThat(HttpLinkProbe.notPublic(InetAddress.getByName(a))).as(a).isTrue();
        }
        for (String a : List.of("8.8.8.8", "1.1.1.1", "172.32.0.1", "93.184.216.34", "2606:4700:4700::1111")) {
            assertThat(HttpLinkProbe.notPublic(InetAddress.getByName(a))).as(a).isFalse();
        }
    }
}
