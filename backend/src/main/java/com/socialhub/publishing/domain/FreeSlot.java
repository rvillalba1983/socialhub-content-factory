package com.socialhub.publishing.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * La siguiente hora libre de una marca y red: la primera, redondeada al cuarto de hora, que no queda antes de un momento
 * dado y que no tiene otra publicación activa a menos de {@link #SPACING}.
 */
public final class FreeSlot {

    /** Entre dos publicaciones de la misma marca y red tiene que haber al menos esto. */
    public static final Duration SPACING = Duration.ofMinutes(30);

    private FreeSlot() {}

    /** El primer cuarto de hora (en su zona) que no es anterior a {@code at}. */
    public static Instant ceilQuarter(Instant at, ZoneId zone) {
        var local = at.atZone(zone);
        var minute = local.truncatedTo(ChronoUnit.MINUTES);
        if (minute.toInstant().isBefore(at)) minute = minute.plusMinutes(1);
        int rest = minute.getMinute() % 15;
        return (rest == 0 ? minute : minute.plusMinutes(15 - rest)).toInstant();
    }

    /** Hay otra publicación a menos de {@code spacing} (por delante o por detrás). */
    public static boolean clashes(Instant at, Collection<Instant> taken, Duration spacing) {
        return taken.stream().anyMatch(t -> Duration.between(t, at).abs().compareTo(spacing) < 0);
    }

    /** La primera hora libre a partir de {@code notBefore} (incluida, si ya es un cuarto de hora y está libre). */
    public static Instant next(Instant notBefore, ZoneId zone, Collection<Instant> taken, Duration spacing) {
        Instant candidate = ceilQuarter(notBefore, zone);
        while (true) {
            Instant current = candidate;
            var conflict = taken.stream().filter(t -> Duration.between(t, current).abs().compareTo(spacing) < 0)
                    .max(java.util.Comparator.naturalOrder());
            if (conflict.isEmpty()) return candidate;
            candidate = ceilQuarter(conflict.get().plus(spacing), zone);
        }
    }
}
