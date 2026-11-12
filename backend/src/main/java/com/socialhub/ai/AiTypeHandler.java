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

    /**
     * Completa los datos de entrada al crear el encargo, para que lo que depende del momento (por ejemplo, «el lunes que
     * viene») quede escrito en el encargo y no se recalcule después.
     */
    default java.util.List<String> completeInputs(java.util.List<String> inputs) { return inputs; }

    /**
     * Si tiene sentido pedir ya el encargo semanal automático de esa promoción y semana (el lunes). Si no, no se crea ni
     * se marca como hecho, y se vuelve a mirar en la ronda siguiente.
     */
    default boolean readyForAutoOrder(String promotionId, java.time.LocalDate week) { return true; }

    /**
     * Las publicaciones (u otros datos de entrada) de las que toca pedir ya un encargo automático para esa promoción, si
     * toca alguno (por ejemplo, un spec por lotes). Vacío si no toca nada. Lo usa SocialHub en cada ronda: el manejador
     * evita repetir lo que ya está en un encargo abierto.
     */
    default java.util.List<String> autoOrderBatch(String promotionId, java.time.Instant now) { return java.util.List.of(); }

    /**
     * Como {@link #completeInputs(java.util.List)}, sabiendo la promoción: para completar los datos de entrada con lo que
     * hay pendiente en ella.
     */
    default java.util.List<String> completeInputs(String promotionId, java.util.List<String> inputs) { return completeInputs(inputs); }

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
