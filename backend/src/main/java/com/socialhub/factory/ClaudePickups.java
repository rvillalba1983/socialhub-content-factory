package com.socialhub.factory;

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
final class ClaudePickups {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final List<LocalTime> TIMES = List.of(LocalTime.of(9, 40), LocalTime.of(14, 40), LocalTime.of(20, 40));
    /** Margen antes de dar por no recogido un encargo. */
    static final Duration GRACE = Duration.ofMinutes(30);
    /** Lo que tarda un encargo recién escrito en llegar a Drive y a la nube: pasado ese margen, ya lo puede ver Claude. */
    static final Duration SYNC_MARGIN = Duration.ofMinutes(5);

    private ClaudePickups() {}

    /** La primera recogida estrictamente posterior a ese instante (tras la última del día, la primera del siguiente). */
    static ZonedDateTime nextAfter(Instant instant) {
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
    static ZonedDateTime pickupFor(Instant sentAt) {
        return nextAfter(sentAt.plus(SYNC_MARGIN));
    }

    /** La recogida que tocaba para un encargo enviado en ese instante ya pasó hace más del margen. */
    static boolean overdue(Instant sentAt, Instant now) {
        return !pickupFor(sentAt).toInstant().plus(GRACE).isAfter(now);
    }
}
