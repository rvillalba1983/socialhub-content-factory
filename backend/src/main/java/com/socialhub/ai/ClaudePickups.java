package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Horas fijas a las que la tarea programada «Encargos de SocialHub» recoge los encargos, en hora de España. Son una
 * constante de SocialHub: las tareas programadas de Claude no se tocan desde aquí.
 */
public final class ClaudePickups {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final List<LocalTime> TIMES = List.of(LocalTime.of(9, 40), LocalTime.of(14, 40), LocalTime.of(20, 40));
    /** Margen antes de dar por no recogido un encargo. */
    static final Duration GRACE = Duration.ofMinutes(30);
    /** Lo que tarda un encargo recién escrito en llegar a Drive y a la nube: pasado ese margen, ya lo puede ver Claude. */
    static final Duration SYNC_MARGIN = Duration.ofMinutes(5);

    private ClaudePickups() {}

    /** La primera recogida estrictamente posterior a ese instante (tras la última del día, la primera del siguiente). */
    public static ZonedDateTime nextAfter(Instant instant) {
        ZonedDateTime at = instant.atZone(ZONE);
        for (int day = 0; day <= 1; day++) {
            for (LocalTime time : TIMES) {
                ZonedDateTime candidate = ZonedDateTime.of(at.toLocalDate().plusDays(day), time, ZONE);
                if (candidate.toInstant().isAfter(instant)) return candidate;
            }
        }
        throw new IllegalStateException("Sin recogida siguiente");
    }

    /** La recogida que le toca a un encargo enviado en ese instante, contando el margen de sincronización. */
    public static ZonedDateTime pickupFor(Instant sentAt) {
        return nextAfter(sentAt.plus(SYNC_MARGIN));
    }

    /** La recogida que tocaba para un encargo enviado en ese instante ya pasó hace más del margen. */
    public static boolean overdue(Instant sentAt, Instant now) {
        return !pickupFor(sentAt).toInstant().plus(GRACE).isAfter(now);
    }

    /** «H:MM» de una hora, sin ceros a la izquierda en la hora. */
    public static String hour(ZonedDateTime time) {
        return time.getHour() + ":" + String.format("%02d", time.getMinute());
    }

    /** Lo máximo que tarda el envío de un encargo en cola (la ronda del envío es cada 30 s). */
    public static final Duration DISPATCH_MAX = Duration.ofSeconds(30);

    /**
     * Cuándo llegará lo que se pide ahora: la recogida que le tocará contando lo que tarda en salir (hasta 30 s) y el
     * margen de sincronización. «sobre las 14:40», o «mañana sobre las 9:40» si la recogida es otro día.
     */
    public static String etaText(Instant now) {
        return etaText(now.plus(DISPATCH_MAX), now);
    }

    /** Cuándo llegará un encargo ya enviado en {@code sentAt}, visto desde {@code now}. */
    public static String etaText(Instant sentAt, Instant now) {
        ZonedDateTime next = pickupFor(sentAt);
        boolean tomorrow = next.toLocalDate().isAfter(now.atZone(ZONE).toLocalDate());
        return (tomorrow ? "mañana " : "") + "sobre las " + hour(next);
    }
}
