package com.socialhub.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Encargos a Claude que siguen en cola por algo que tiene que arreglar Raúl (por ejemplo, la marca sin carpeta de
 * «Encargos»). Salen en «Necesita atención». Se recalcula en cada envío, así que no hace falta guardarlo.
 */
@Component
public class AiHeldOrders {

    /** Un encargo retenido: la tarea, su estación, su título y qué falta. */
    public record Held(String taskId, String stationId, String title, String reason) {}

    private volatile Map<String, Held> held = Map.of();

    /** Los encargos retenidos ahora, del más antiguo al más nuevo. */
    public List<Held> current() {
        return List.copyOf(held.values());
    }

    void replace(Map<String, Held> now) {
        held = Map.copyOf(now);
    }

    static Map<String, Held> collector() {
        return new ConcurrentHashMap<>();
    }
}
