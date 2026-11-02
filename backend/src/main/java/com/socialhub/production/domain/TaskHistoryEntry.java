package com.socialhub.production.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/** Una línea del historial visible de una tarea. */
@ValueObject
public record TaskHistoryEntry(Instant at, String message) {}
