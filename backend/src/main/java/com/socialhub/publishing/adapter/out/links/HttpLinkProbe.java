package com.socialhub.publishing.adapter.out.links;

import com.socialhub.publishing.domain.LinkProbe;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Comprueba enlaces con {@code java.net.http}, sin librerías. Seguridad (SSRF): solo https; cada salto (también tras
 * redirecciones, que se siguen a mano) resuelve el nombre y se rechaza si alguna de sus direcciones es privada, local, de
 * enlace local, de metadatos o no enrutable. Sin cookies ni credenciales, User-Agent neutro, tiempo corto con un
 * reintento y como mucho 4 KB descargados.
 */
@Component
class HttpLinkProbe implements LinkProbe {

    static final String USER_AGENT = "LinkCheck/1.0";
    static final int MAX_REDIRECTS = 5;
    static final int MAX_BYTES = 4096;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);

    /** Qué se permite; las pruebas con servidor local usan {@link Policy#loopbackOverHttp()}. */
    record Policy(boolean httpsOnly, Predicate<InetAddress> forbidden) {
        static Policy strict() { return new Policy(true, HttpLinkProbe::notPublic); }

        /** Solo para pruebas: http y 127.0.0.1 permitidos; el resto de direcciones privadas siguen prohibidas. */
        static Policy loopbackOverHttp() {
            return new Policy(false, a -> !a.isLoopbackAddress() && notPublic(a));
        }
    }

    private final Policy policy;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    HttpLinkProbe() { this(Policy.strict()); }

    HttpLinkProbe(Policy policy) { this.policy = policy; }

    @Override
    public State probe(String url) {
        State state = once(url);
        return state == State.NOT_RESPONDING ? once(url) : state;
    }

    private State once(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return State.REFUSED;
        }
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!allowed(uri)) return State.REFUSED;
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET()
                        .header("User-Agent", USER_AGENT).header("Accept", "*/*").header("Range", "bytes=0-" + (MAX_BYTES - 1)).build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    body.readNBytes(MAX_BYTES);
                }
                int code = response.statusCode();
                if (code >= 300 && code < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null) return State.NOT_RESPONDING;
                    uri = uri.resolve(location);
                    continue;
                }
                // 206: el servidor atendió el rango pedido
                return code >= 200 && code < 300 ? State.OK : State.NOT_RESPONDING;
            }
            return State.NOT_RESPONDING;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return State.NOT_RESPONDING;
        } catch (IOException | RuntimeException e) {
            return State.NOT_RESPONDING;
        }
    }

    /** https (salvo en pruebas), con nombre de equipo y sin credenciales, y solo a direcciones públicas. */
    private boolean allowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean schemeOk = scheme.equals("https") || (!policy.httpsOnly() && scheme.equals("http"));
        if (!schemeOk || uri.getHost() == null || uri.getUserInfo() != null) return false;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) return false;
            for (InetAddress a : addresses) if (policy.forbidden().test(a)) return false;
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Direcciones a las que nunca se llama: locales, privadas, de enlace local (metadatos 169.254.169.254), CGNAT, etc. */
    static boolean notPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) return privateV4(b[0] & 0xff, b[1] & 0xff);
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return true; // fc00::/7, direcciones locales únicas
            boolean mapped = true; // ::ffff:a.b.c.d, una IPv4 con otra forma
            for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) return privateV4(b[12] & 0xff, b[13] & 0xff);
            boolean compat = true; // ::a.b.c.d y 64:ff9b::/96
            for (int i = 0; i < 12; i++) compat &= b[i] == 0;
            return compat;
        }
        return false;
    }

    private static boolean privateV4(int first, int second) {
        return first == 0 || first == 10 || first == 127 || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168) || (first == 192 && second == 0) || (first == 198 && (second == 18 || second == 19))
                || first >= 224;
    }
}
