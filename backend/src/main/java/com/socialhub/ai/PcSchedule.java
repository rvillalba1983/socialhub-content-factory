package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Las pasadas de la tarea programada «Publicar con Chrome» del PC de Raúl, que hace los encargos de
 * {@code para-claude-pc}: cada 5 horas, 20 minutos después de las de la nube (0:50, 5:50, 10:50, 15:50 y 20:50, hora de
 * España; el PC no se apaga nunca). Las horas están en {@link ClaudePasses}. Una pasada de las 8:00 a las 18:00 solo
 * hace los encargos urgentes.
 */
public final class PcSchedule {

    public static final ZoneId ZONE = ClaudePasses.ZONE;
    /** Lo que se espera tras la pasada que le tocaba antes de dar por parada la tarea (hacer el encargo con Chrome lleva rato). */
    public static final Duration LATE_AFTER = Duration.ofMinutes(90);
    /** Lo que tarda un encargo recién escrito en llegar a Drive y al PC. */
    static final Duration SYNC_MARGIN = ClaudePickups.SYNC_MARGIN;

    private PcSchedule() {}

    /** La pasada que le toca a un encargo del PC enviado en ese instante, contando el margen de sincronización. */
    public static ZonedDateTime passFor(Instant sentAt, boolean urgent) {
        return ClaudePasses.next(true, sentAt.plus(SYNC_MARGIN), urgent);
    }

    /** Un encargo enviado en ese instante lleva sin contestar 90 minutos o más desde la pasada que le tocaba. */
    public static boolean late(Instant sentAt, Instant now, boolean urgent) {
        return !passFor(sentAt, urgent).toInstant().plus(LATE_AFTER).isAfter(now);
    }

    /**
     * Si un encargo del PC que se envía ahora llega a una pasada que lo haga (contando la sincronización) con tiempo de
     * sobra antes de su límite.
     */
    public static boolean canMeet(Instant deadline, Instant now) {
        return ClaudePasses.canMeet(true, deadline, now.plus(ClaudePickups.DISPATCH_MAX).plus(SYNC_MARGIN));
    }

    /** La última pasada de la tarea (de cualquier tipo) que cae en ese instante o antes. */
    public static Instant lastPassAtOrBefore(Instant instant) {
        return ClaudePasses.lastAtOrBefore(true, instant);
    }
}
