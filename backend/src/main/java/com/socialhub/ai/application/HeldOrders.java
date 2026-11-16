package com.socialhub.ai.application;

import com.socialhub.ai.AiHeldOrders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Lo retenido y lo parado en la última ronda de envío; se recalcula en cada una, así que no se guarda. */
@Component
class HeldOrders implements AiHeldOrders {

    private volatile Map<String, Held> held = Map.of();
    private volatile Map<String, Stalled> stalled = Map.of();

    @Override
    public List<Held> current() {
        return List.copyOf(held.values());
    }

    @Override
    public List<Stalled> stalledProposals() {
        return List.copyOf(stalled.values());
    }

    /** @return si lo retenido o lo parado ha cambiado respecto a la ronda anterior */
    boolean replace(Map<String, Held> now, Map<String, Stalled> stalledNow) {
        Map<String, Held> next = Map.copyOf(now);
        Map<String, Stalled> nextStalled = Map.copyOf(stalledNow);
        boolean changed = !next.equals(held) || !nextStalled.equals(stalled);
        held = next;
        stalled = nextStalled;
        return changed;
    }

    static Map<String, Held> collector() {
        return new ConcurrentHashMap<>();
    }

    static Map<String, Stalled> stalledCollector() {
        return new ConcurrentHashMap<>();
    }
}
