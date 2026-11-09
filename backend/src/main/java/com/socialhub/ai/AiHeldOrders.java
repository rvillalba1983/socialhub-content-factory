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

    /**
     * Un encargo retenido: la tarea, su estación, su título y qué falta. {@code promotionId} y {@code type} permiten
     * distinguir, por ejemplo, las propuestas de una promoción (vacíos si la tarea no los tiene).
     */
    public record Held(String taskId, String stationId, String title, String reason,
                       @org.jspecify.annotations.Nullable String promotionId,
                       @org.jspecify.annotations.Nullable AiRequestType type) {

        /** Son las propuestas de la semana de una promoción concreta. */
        public boolean isPromotionProposals() {
            return type == AiRequestType.WEEKLY_PROPOSALS && promotionId != null;
        }
    }

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
