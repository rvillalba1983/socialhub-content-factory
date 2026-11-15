package com.socialhub.ai;

import com.socialhub.ai.domain.AiRequestType;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Alta de encargos a Claude. Quedan en cola y salen hacia la carpeta de Drive de la marca. */
@PrimaryPort
public interface AiOrders {

    /** Si el último envío de ese encargo salió marcado como urgente (vacío si aún no ha salido). */
    Optional<Boolean> urgentOf(String taskId);

    /**
     * Alta de un encargo pedido por Raúl desde la interfaz: sale siempre como urgente (se hace en la siguiente pasada,
     * aunque sea de día). Lo que pide SocialHub por su cuenta va por {@link #submitInternal}.
     *
     * @return identificador de la tarea
     */
    String submit(String promotionId, AiRequestType type, String title, String goal, List<String> inputs);

    /** Alta de un encargo que pide SocialHub por su cuenta (también los de publicación con Chrome en el PC): sigue la regla de urgencia. */
    String submitInternal(String promotionId, AiRequestType type, String title, String goal, List<String> inputs);
}
