package com.socialhub.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** Cuándo toca pedir cada encargo semanal automático (hora de España). */
public final class AiWeeklySchedule {

    private AiWeeklySchedule() {}

    /**
     * Lunes de la semana cuyo spec toca pedir ahora, si toca alguno: el domingo desde las 9:00, el de la semana que
     * empieza al día siguiente; de lunes a sábado, el de la semana en curso (por si SocialHub estaba cerrado o aún no
     * había publicaciones aprobadas). El domingo antes de las 9:00 no toca nada.
     */
    public static Optional<LocalDate> specWeek(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return now.toLocalTime().isBefore(AiAutoOrders.FROM) ? Optional.empty() : Optional.of(today.plusDays(1));
        }
        return Optional.of(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }
}
