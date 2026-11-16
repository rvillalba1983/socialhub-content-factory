package com.socialhub.ai;

import com.socialhub.ai.domain.ClaudePickups;
import com.socialhub.ai.domain.PcSchedule;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Cómo responde Claude, deducido de los encargos y resultados que SocialHub ya tiene (sin llamadas externas): cuándo
 * llegó el último resultado y si hay encargos sin contestar. Se separan los de la nube (tarea «Encargos de SocialHub»)
 * de los del PC (tarea «Publicar con Chrome»).
 */
@PrimaryPort
public interface ClaudeActivity {

    /** {@code count} encargos sin contestar, el más antiguo enviado en {@code since}. */
    record Silence(int count, Instant since) {}

    /** Cuándo llegó el último resultado de un encargo del PC ({@code onPc}) o de la nube, si ha llegado alguno. */
    Optional<Instant> lastResult(boolean onPc);

    /** Se ha enviado alguna vez algún encargo del PC ({@code onPc}) o de la nube. */
    boolean everSent(boolean onPc);

    /**
     * Encargos de la nube sin resultado tras la recogida que les tocaba más 30 minutos ({@link ClaudePickups}). No
     * cuentan los de una marca cuya carpeta de «Encargos» no está disponible: no se sabe si Claude ha contestado.
     */
    static Optional<Silence> cloudSilence(List<AiSentOrders.Sent> sent, Instant now) {
        return silence(sent.stream().filter(s -> !s.onPc() && s.ordersAvailable() && ClaudePickups.overdue(s.sentAt(), now, s.urgent())).toList());
    }

    /**
     * Encargos del PC sin resultado 90 minutos o más después de la pasada que les tocaba ({@link PcSchedule}).
     * Misma regla de la carpeta de «Encargos» que en {@link #cloudSilence}.
     */
    static Optional<Silence> pcSilence(List<AiSentOrders.Sent> sent, Instant now) {
        return silence(sent.stream().filter(s -> s.onPc() && s.ordersAvailable() && PcSchedule.late(s.sentAt(), now, s.urgent())).toList());
    }

    /** Hay encargos enviados de ese tipo (nube o PC) esperando, tarde o no. */
    static boolean anyWaiting(List<AiSentOrders.Sent> sent, boolean onPc) {
        return sent.stream().anyMatch(s -> s.onPc() == onPc);
    }

    private static Optional<Silence> silence(List<AiSentOrders.Sent> late) {
        return late.stream().map(AiSentOrders.Sent::sentAt).min(Comparator.naturalOrder())
                .map(since -> new Silence(late.size(), since));
    }

    /** «11/11/2026 14:42», en hora de España. */
    static String dateTime(Instant instant) {
        return DateTimeFormatter.ofPattern("d/M/yyyy HH:mm").format(instant.atZone(ClaudePickups.ZONE));
    }

    /** «las 9:30» si es de hoy, «ayer a las 9:30» o «el 9/11 a las 9:30». */
    static String since(Instant instant, Instant now) {
        ZonedDateTime at = instant.atZone(ClaudePickups.ZONE);
        var today = now.atZone(ClaudePickups.ZONE).toLocalDate();
        String hour = "las " + ClaudePickups.hour(at);
        if (at.toLocalDate().equals(today)) return hour;
        if (at.toLocalDate().equals(today.minusDays(1))) return "ayer a " + hour;
        return "el " + at.getDayOfMonth() + "/" + at.getMonthValue() + " a " + hour;
    }
}
