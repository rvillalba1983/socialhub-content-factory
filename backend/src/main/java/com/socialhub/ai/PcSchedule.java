package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Horario de la tarea programada «Publicar con Chrome» del PC de Raúl, que hace los encargos de {@code para-claude-pc}:
 * corre cada hora a y 40, de 8:40 a 22:40, hora de España. Es una constante de SocialHub, como las recogidas de
 * {@link ClaudePickups}: las tareas programadas de Claude no se tocan desde aquí. Fuera de ese horario la tarea no
 * puede contestar, así que ese tiempo no cuenta como retraso.
 */
public final class PcSchedule {

    public static final ZoneId ZONE = ClaudePickups.ZONE;
    static final LocalTime OPENS = LocalTime.of(8, 40);
    static final LocalTime CLOSES = LocalTime.of(22, 40);
    /** Tiempo de horario sin respuesta a partir del cual se da por parada la tarea. */
    public static final Duration LATE_AFTER = Duration.ofMinutes(90);

    private PcSchedule() {}

    /** Cuánto tiempo de horario (8:40-22:40 de cada día) hay entre dos instantes. */
    public static Duration activeTime(Instant from, Instant to) {
        if (!to.isAfter(from)) return Duration.ZERO;
        LocalDate last = to.atZone(ZONE).toLocalDate();
        Duration total = Duration.ZERO;
        for (LocalDate day = from.atZone(ZONE).toLocalDate(); !day.isAfter(last); day = day.plusDays(1)) {
            Instant start = max(ZonedDateTime.of(day, OPENS, ZONE).toInstant(), from);
            Instant end = min(ZonedDateTime.of(day, CLOSES, ZONE).toInstant(), to);
            if (end.isAfter(start)) total = total.plus(Duration.between(start, end));
        }
        return total;
    }

    /** Un encargo enviado en ese instante lleva sin contestar 90 minutos o más dentro del horario de la tarea. */
    public static boolean late(Instant sentAt, Instant now) {
        return activeTime(sentAt, now).compareTo(LATE_AFTER) >= 0;
    }

    /** La última pasada de la tarea (a y 40, de 8:40 a 22:40) que cae en ese instante o antes. */
    public static Instant lastPassAtOrBefore(Instant instant) {
        ZonedDateTime at = instant.atZone(ZONE);
        for (int day = 0; day <= 2; day++) {
            LocalDate date = at.toLocalDate().minusDays(day);
            for (int hour = CLOSES.getHour(); hour >= OPENS.getHour(); hour--) {
                ZonedDateTime pass = ZonedDateTime.of(date, LocalTime.of(hour, OPENS.getMinute()), ZONE);
                if (!pass.toInstant().isAfter(instant)) return pass.toInstant();
            }
        }
        throw new IllegalStateException("Sin pasada anterior");
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }

    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
