package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * El horario de las dos tareas programadas de Claude, en un solo sitio (hora de España). Es una constante de SocialHub:
 * las tareas programadas de Claude no se tocan desde aquí.
 * <ul>
 *   <li>Nube, «Encargos de SocialHub» (carpeta {@code para-claude}): una pasada cada 5 horas desde las 0:30 (0:30, 5:30,
 *       10:30, 15:30 y 20:30).</li>
 *   <li>PC, «Publicar con Chrome» (carpeta {@code para-claude-pc}): 20 minutos después de cada pasada de la nube (0:50,
 *       5:50, 10:50, 15:50 y 20:50). El PC no se apaga nunca.</li>
 *   <li>Preferencia nocturna: las pasadas que caen entre las 8:00 y las 18:00 solo hacen los encargos urgentes (los que
 *       vencerían antes de la siguiente pasada completa, la de la noche). Las demás lo hacen todo.</li>
 * </ul>
 */
public final class ClaudePasses {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** Primera pasada de la nube del día. */
    static final LocalTime FIRST = LocalTime.of(0, 30);
    /** Tiempo entre pasadas. */
    static final Duration INTERVAL = Duration.ofHours(5);
    /** Lo que se retrasa la pasada del PC respecto a la de la nube. */
    static final Duration PC_DELAY = Duration.ofMinutes(20);
    /** Entre estas horas (incluidas), la pasada solo hace los encargos urgentes. */
    public static final LocalTime URGENT_ONLY_FROM = LocalTime.of(8, 0);
    public static final LocalTime URGENT_ONLY_UNTIL = LocalTime.of(18, 0);
    /** Margen entre el fin de una pasada y el límite de un encargo: lo que tarda en hacerlo. */
    public static final Duration PASS_MARGIN = Duration.ofMinutes(10);

    private static final List<LocalTime> CLOUD = times(Duration.ZERO);
    private static final List<LocalTime> PC = times(PC_DELAY);

    private ClaudePasses() {}

    private static List<LocalTime> times(Duration delay) {
        List<LocalTime> result = new ArrayList<>();
        for (LocalTime t = FIRST; ; t = t.plus(INTERVAL)) {
            result.add(t.plus(delay));
            if (t.plus(INTERVAL).isBefore(t)) break; // dio la vuelta a medianoche
        }
        return List.copyOf(result);
    }

    /** Horas de pasada de la nube ({@code onPc} falso) o del PC, de la primera a la última del día. */
    public static List<LocalTime> times(boolean onPc) { return onPc ? PC : CLOUD; }

    /** La pasada que cae a esa hora solo hace los encargos urgentes. */
    public static boolean urgentOnly(LocalTime time) {
        return !time.isBefore(URGENT_ONLY_FROM) && !time.isAfter(URGENT_ONLY_UNTIL);
    }

    /**
     * La primera pasada estrictamente posterior a ese instante. Con {@code urgent} falso, solo cuentan las completas (las
     * urgentes las hacen todas).
     */
    public static ZonedDateTime next(boolean onPc, Instant after, boolean urgent) {
        ZonedDateTime at = after.atZone(ZONE);
        for (int day = 0; day <= 2; day++) {
            for (LocalTime time : times(onPc)) {
                if (!urgent && urgentOnly(time)) continue;
                ZonedDateTime candidate = ZonedDateTime.of(at.toLocalDate().plusDays(day), time, ZONE);
                if (candidate.toInstant().isAfter(after)) return candidate;
            }
        }
        throw new IllegalStateException("Sin pasada siguiente");
    }

    /** La última pasada (de cualquier tipo) que cae en ese instante o antes. */
    public static Instant lastAtOrBefore(boolean onPc, Instant instant) {
        ZonedDateTime at = instant.atZone(ZONE);
        List<LocalTime> times = times(onPc);
        for (int day = 0; day <= 2; day++) {
            LocalDate date = at.toLocalDate().minusDays(day);
            for (int i = times.size() - 1; i >= 0; i--) {
                Instant pass = ZonedDateTime.of(date, times.get(i), ZONE).toInstant();
                if (!pass.isAfter(instant)) return pass;
            }
        }
        throw new IllegalStateException("Sin pasada anterior");
    }

    /**
     * Si un encargo con ese límite, escrito en {@code at}, es urgente: no puede esperar a la siguiente pasada completa
     * (esa pasada, más el margen, ya queda fuera de su límite). Sin límite, nunca es urgente.
     */
    public static boolean urgent(boolean onPc, @Nullable Instant deadline, Instant at) {
        if (deadline == null) return false;
        return next(onPc, at, false).toInstant().plus(PASS_MARGIN).isAfter(deadline);
    }

    /**
     * Si alguna pasada que haga ese encargo cae, con el margen, antes de su límite: la primera que lo recoge es la
     * siguiente a {@code at} (cualquiera si es urgente; si no, la siguiente completa).
     */
    public static boolean canMeet(boolean onPc, Instant deadline, Instant at) {
        boolean urgent = urgent(onPc, deadline, at);
        return !next(onPc, at, urgent).toInstant().plus(PASS_MARGIN).isAfter(deadline);
    }
}
