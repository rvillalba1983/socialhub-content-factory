package com.socialhub.production.domain;

/** Una estación con el recuento de sus tareas. */
public record StationSummary(Station station, TaskCounts counts) {}
