package com.socialhub.flows;

import com.socialhub.PinnedClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reloj de las pruebas de flujo: arranca en el mismo momento fijo que {@link PinnedClock} (por defecto, el martes
 * 10 nov 2026 a las 10:00 de Madrid; se cambia con {@code -Dsocialhub.test.now=...}) y avanza con el tiempo real, pero se
 * puede adelantar a mano ({@link #set}, {@link #forward}) para recorrer plazos de horas o de días sin esperar.
 * Cada clase de prueba crea el suyo, para que dos contextos en caché no se pisen el reloj.
 */
public final class FlowClock extends Clock {

    public static final ZoneId MADRID = PinnedClock.MADRID;

    private final Instant start = LocalDateTime
            .parse(System.getProperty("socialhub.test.now", PinnedClock.DEFAULT_NOW)).atZone(MADRID).toInstant();
    private volatile Duration offset = Duration.between(Instant.now(), start);

    /** El momento fijo en el que empieza cada prueba. */
    public Instant start() { return start; }

    /** Vuelve al momento de arranque (sigue avanzando con el tiempo real desde ahí). */
    public void reset() { offset = Duration.between(Instant.now(), start); }

    public void set(Instant at) { offset = Duration.between(Instant.now(), at); }

    public void forward(Duration amount) { offset = offset.plus(amount); }

    @Override public ZoneId getZone() { return MADRID; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return Instant.now().plus(offset); }
}
