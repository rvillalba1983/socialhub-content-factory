package com.socialhub.production.domain;

import java.util.List;

/** Las estaciones (con su recuento) y todas las tareas, leídas a la vez. */
public record ProductionOverview(List<StationSummary> stations, List<Task> tasks) {}
