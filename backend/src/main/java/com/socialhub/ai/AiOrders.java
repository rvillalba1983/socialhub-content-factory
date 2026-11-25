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

    /** Promoción a la que pertenece la tarea de ese encargo (vacío si no consta). */
    Optional<String> promotionOf(String taskId);

    /**
     * Alta de un encargo pedido por Raúl desde la interfaz: sale siempre como urgente (se hace en la siguiente pasada,
     * aunque sea de día). Lo que pide SocialHub por su cuenta va por {@link #submitInternal}.
     *
     * @return identificador de la tarea
     */
    String submit(String promotionId, AiRequestType type, String title, String goal, List<String> inputs);

    /** Alta de un encargo que pide SocialHub por su cuenta (también los de publicación con Chrome en el PC): sigue la regla de urgencia. */
    String submitInternal(String promotionId, AiRequestType type, String title, String goal, List<String> inputs);

    /**
     * Deja una sola copia viva de un encargo por dato de entrada (por ejemplo, una publicación): los demás encargos abiertos
     * del mismo tipo con ese dato (en cola, en espera de Claude, bloqueados o fallidos con reintentos) se cancelan. Si ya
     * salieron a Drive y Claude aún no ha dejado su resultado, SocialHub retira su archivo del encargo para que nadie lo haga.
     *
     * @param exceptTaskId el encargo que se queda (el nuevo), o {@code null} si no hay ninguno
     * @return cuántos encargos se han cancelado
     */
    int supersede(String promotionId, AiRequestType type, String inputId, @org.jspecify.annotations.Nullable String exceptTaskId);
}
