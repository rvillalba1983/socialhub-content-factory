package com.socialhub.platform.live;

/**
 * Algo ha cambiado en la fábrica. Se publica tras confirmar la transacción y llega al navegador por SSE.
 *
 * @param kind tipo de elemento (task, station, publication…)
 * @param id identificador del elemento, o null
 */
public record FactoryChanged(String kind, String id) {}
