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

    /** Por qué una tarea de propuestas está parada aunque no esté «retenida» por algo que se arregle en la marca. */
    public enum Cause { DRIVE_UNAVAILABLE, DAILY_LIMIT }

    /**
     * Propuestas de una promoción que no salen: la carpeta de «Encargos» está configurada pero no disponible en este
     * ordenador, o se ha alcanzado el límite diario ({@code limit}, 0 si no aplica). No salen como alerta de tarea: la
     * tarea ya lo cuenta en su historial y aquí solo sirve para la fila de «Qué hacer ahora».
     */
    public record Stalled(String promotionId, Cause cause, int limit) {}

    private volatile Map<String, Held> held = Map.of();
    private volatile Map<String, Stalled> stalled = Map.of();

    /** Los encargos retenidos ahora, del más antiguo al más nuevo. */
    public List<Held> current() {
        return List.copyOf(held.values());
    }

    /** Las propuestas paradas por Drive o por el límite diario, de la más antigua a la más nueva. */
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
