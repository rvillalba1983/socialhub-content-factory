package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Las pasadas de la tarea programada «Encargos de SocialHub» (la nube), en hora de España. Las horas están en
 * {@link ClaudePasses}. Una pasada de las 8:00 a las 18:00 solo recoge los encargos urgentes; el resto espera a la
 * siguiente pasada completa.
 */
public final class ClaudePickups {

    public static final ZoneId ZONE = ClaudePasses.ZONE;
    /** Margen antes de dar por no recogido un encargo. */
    static final Duration GRACE = Duration.ofMinutes(30);
    /** Lo que tarda un encargo recién escrito en llegar a Drive y a la nube: pasado ese margen, ya lo puede ver Claude. */
    public static final Duration SYNC_MARGIN = Duration.ofMinutes(5);

    private ClaudePickups() {}

    /** La primera pasada estrictamente posterior a ese instante que recoge un encargo urgente (o no) de la nube. */
    public static ZonedDateTime nextAfter(Instant instant, boolean urgent) {
        return ClaudePasses.next(false, instant, urgent);
    }

    /** La pasada que le toca a un encargo enviado en ese instante, contando el margen de sincronización. */
    public static ZonedDateTime pickupFor(Instant sentAt, boolean urgent) {
        return nextAfter(sentAt.plus(SYNC_MARGIN), urgent);
    }

    /** La pasada que tocaba para un encargo enviado en ese instante ya pasó hace más del margen. */
    public static boolean overdue(Instant sentAt, Instant now, boolean urgent) {
        return !pickupFor(sentAt, urgent).toInstant().plus(GRACE).isAfter(now);
    }

    /** «H:MM» de una hora, sin ceros a la izquierda en la hora. */
    public static String hour(ZonedDateTime time) {
        return time.getHour() + ":" + String.format("%02d", time.getMinute());
    }

    /** Lo máximo que tarda el envío de un encargo en cola (la ronda del envío es cada 30 s). */
    public static final Duration DISPATCH_MAX = Duration.ofSeconds(30);

    /**
     * Cuándo llegará lo que se pide ahora: la recogida que le tocará contando lo que tarda en salir (hasta 30 s) y el
     * margen de sincronización. «sobre las 15:30», o «mañana sobre las 0:30» si la recogida es otro día.
     */
    public static String etaText(Instant now, boolean urgent) {
        return etaText(now.plus(DISPATCH_MAX), now, urgent);
    }

    /** Cuándo llegará un encargo ya enviado en {@code sentAt}, visto desde {@code now}. */
    public static String etaText(Instant sentAt, Instant now, boolean urgent) {
        ZonedDateTime next = pickupFor(sentAt, urgent);
        boolean tomorrow = next.toLocalDate().isAfter(now.atZone(ZONE).toLocalDate());
        return (tomorrow ? "mañana " : "") + "sobre las " + hour(next);
    }
}
