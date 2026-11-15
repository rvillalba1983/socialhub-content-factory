package com.socialhub.ai;

import com.socialhub.ai.domain.AiRequestType;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Encargos a Claude que ya están en Drive y esperan a que los recoja. */
@PrimaryPort
public interface AiSentOrders {

    /**
     * Un encargo enviado y sin resultado. {@code onPc}: lo hace Claude con Chrome en el PC (otra carpeta y otra tarea).
     * {@code urgent}: se marcó urgente al enviarlo (lo recoge cualquier pasada, no solo las completas).
     * {@code ordersAvailable}: la carpeta de «Encargos» de su marca está disponible ahora en este ordenador; si no lo
     * está, no se sabe si Claude ha dejado su resultado.
     */
    record Sent(@Nullable String promotionId, AiRequestType type, Instant sentAt, boolean onPc,
                boolean ordersAvailable, boolean urgent) {

        public Sent(@Nullable String promotionId, AiRequestType type, Instant sentAt, boolean onPc, boolean ordersAvailable) {
            this(promotionId, type, sentAt, onPc, ordersAvailable, false);
        }
    }

    /**
     * Los encargos enviados cuya tarea sigue esperando a Claude, del más antiguo al más nuevo. Si la tarea se ha
     * cancelado o ya no espera, el encargo no cuenta aunque su petición siga como enviada.
     */
    List<Sent> waiting();
}
