package com.socialhub.publishing.application;

import com.socialhub.publishing.domain.LinkProbe;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guarda lo que respondió cada enlace para no ir a la red cada vez que se abre el control de calidad. Un enlace sin
 * comprobar se pide en segundo plano (como mucho 4 a la vez) y el aviso se refresca al terminar.
 */
@Component
@RequiredArgsConstructor
class LinkChecks {

    static final Duration OK_VALID = Duration.ofHours(1);
    static final Duration FAILED_VALID = Duration.ofMinutes(5);
    private static final int MAX_CACHED = 2000;

    record Entry(LinkProbe.State state, Instant at) {}

    private final LinkProbe probe;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(4);

    /** Lo último que se sabe del enlace, si no ha caducado. */
    Optional<LinkProbe.State> known(String url) {
        Entry e = cache.get(url);
        if (e == null) return Optional.empty();
        Duration valid = e.state() == LinkProbe.State.OK ? OK_VALID : FAILED_VALID;
        return Duration.between(e.at(), clock.instant()).compareTo(valid) > 0 ? Optional.empty() : Optional.of(e.state());
    }

    /** Comprueba el enlace ahora (bloquea hasta tener respuesta) y lo guarda. */
    LinkProbe.State checkNow(String url) {
        LinkProbe.State state = probe.probe(url);
        if (cache.size() >= MAX_CACHED) cache.clear();
        cache.put(url, new Entry(state, clock.instant()));
        return state;
    }

    /** Lo pide en segundo plano si no está ya en marcha; al terminar avisa con {@code done}. */
    void request(String url, Runnable done) {
        if (!pending.add(url)) return;
        Thread.ofVirtual().name("link-check").start(() -> {
            try {
                slots.acquireUninterruptibly();
                try {
                    checkNow(url);
                } finally {
                    slots.release();
                }
            } finally {
                pending.remove(url);
            }
            done.run();
        });
    }
}
