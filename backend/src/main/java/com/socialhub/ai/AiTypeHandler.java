package com.socialhub.ai;

import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import tools.jackson.databind.JsonNode;

/**
 * Lo que sabe cada tipo de encargo: qué datos añade al encargo para Claude y qué hace con su resultado. Los módulos
 * que conocen esos datos (piezas, publicaciones…) aportan su manejador como bean.
 */
@SecondaryPort
public interface AiTypeHandler {

    AiRequestType type();

    /**
     * Comprueba los datos de entrada al dar de alta el encargo.
     *
     * @throws IllegalArgumentException si no valen
     */
    default void validateOrder(String promotionId, java.util.List<String> inputs) {}

    /** Hora límite del encargo («deadline» del contrato): pasada esa hora, Claude no debe hacerlo. */
    default java.time.@org.jspecify.annotations.Nullable Instant deadline(AiJob job) { return null; }

    /**
     * Claude no pudo hacerlo (falló, pidió ayuda o devolvió algo que no cumple el contrato). Para que el módulo que lo
     * pidió pueda seguir por otro camino.
     */
    default void onFailed(AiJob job, String reason) {}

    /** Datos que se añaden a «inputs» del encargo, además del título y los datos de entrada. */
    default Map<String, Object> inputs(AiJob job) { return Map.of(); }

    /**
     * Aplica un resultado completado. Debe comprobarlo todo antes de cambiar nada.
     *
     * @param outputs «outputs» del resultado
     * @param result el resultado entero (fuentes, avisos…)
     * @return texto de resultado de la tarea
     * @throws IllegalArgumentException si el resultado no sirve: la tarea queda con error y ese motivo
     */
    String apply(AiJob job, JsonNode outputs, JsonNode result);
}
