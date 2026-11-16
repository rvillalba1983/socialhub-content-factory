package com.socialhub.ai;

import com.socialhub.ai.domain.AiRequestType;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Encargos a Claude que siguen en cola por algo que tiene que arreglar Raúl (por ejemplo, la marca sin carpeta de
 * «Encargos»). Salen en «Necesita atención». Se recalcula en cada envío, así que no hace falta guardarlo.
 */
@PrimaryPort
public interface AiHeldOrders {

    /**
     * Un encargo retenido: la tarea, su estación, su título y qué falta. {@code promotionId} y {@code type} permiten
     * distinguir, por ejemplo, las propuestas de una promoción (vacíos si la tarea no los tiene).
     */
    record Held(String taskId, String stationId, String title, String reason, @Nullable String promotionId,
                @Nullable AiRequestType type) {

        /** Son las propuestas de la semana de una promoción concreta. */
        public boolean isPromotionProposals() {
            return type == AiRequestType.WEEKLY_PROPOSALS && promotionId != null;
        }
    }

    /** Por qué una tarea de propuestas está parada aunque no esté «retenida» por algo que se arregle en la marca. */
    enum Cause { DRIVE_UNAVAILABLE, DAILY_LIMIT }

    /**
     * Propuestas de una promoción que no salen: la carpeta de «Encargos» está configurada pero no disponible en este
     * ordenador, o se ha alcanzado el límite diario ({@code limit}, 0 si no aplica). No salen como alerta de tarea: la
     * tarea ya lo cuenta en su historial y aquí solo sirve para la fila de «Qué hacer ahora».
     */
    record Stalled(String promotionId, Cause cause, int limit) {}

    /** Los encargos retenidos ahora, del más antiguo al más nuevo. */
    List<Held> current();

    /** Las propuestas paradas por Drive o por el límite diario, de la más antigua a la más nueva. */
    List<Stalled> stalledProposals();
}
