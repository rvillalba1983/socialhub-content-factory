package com.socialhub.ai;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cómo responde Claude, deducido de los encargos y resultados que SocialHub ya tiene (sin llamadas externas): cuándo
 * llegó el último resultado y si hay encargos sin contestar. Se separan los de la nube (tarea «Encargos de SocialHub»)
 * de los del PC (tarea «Publicar con Chrome»).
 */
@Component
@RequiredArgsConstructor
public class ClaudeActivity {

    /** {@code count} encargos sin contestar, el más antiguo enviado en {@code since}. */
    public record Silence(int count, Instant since) {}

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d/M/yyyy HH:mm");

    private final AiRequestRepository requests;

    /** Cuándo llegó el último resultado de un encargo del PC ({@code onPc}) o de la nube, si ha llegado alguno. */
    @Transactional(readOnly = true)
    public Optional<Instant> lastResult(boolean onPc) {
        return Optional.ofNullable(requests.lastFinished(types(onPc)));
    }

    /** Se ha enviado alguna vez algún encargo del PC ({@code onPc}) o de la nube. */
    @Transactional(readOnly = true)
    public boolean everSent(boolean onPc) {
        return requests.existsByTypeIn(types(onPc));
    }

    /**
     * Encargos de la nube sin resultado tras la recogida que les tocaba más 30 minutos ({@link ClaudePickups}). No
     * cuentan los de una marca cuya carpeta de «Encargos» no está disponible: no se sabe si Claude ha contestado.
     */
    public static Optional<Silence> cloudSilence(List<AiSentOrders.Sent> sent, Instant now) {
        return silence(sent.stream().filter(s -> !s.onPc() && s.ordersAvailable() && ClaudePickups.overdue(s.sentAt(), now)).toList());
    }

    /**
     * Encargos del PC sin resultado desde hace 90 minutos o más dentro del horario de la tarea ({@link PcSchedule}).
     * Misma regla de la carpeta de «Encargos» que en {@link #cloudSilence}.
     */
    public static Optional<Silence> pcSilence(List<AiSentOrders.Sent> sent, Instant now) {
        return silence(sent.stream().filter(s -> s.onPc() && s.ordersAvailable() && PcSchedule.late(s.sentAt(), now)).toList());
    }

    /** Hay encargos enviados de ese tipo (nube o PC) esperando, tarde o no. */
    public static boolean anyWaiting(List<AiSentOrders.Sent> sent, boolean onPc) {
        return sent.stream().anyMatch(s -> s.onPc() == onPc);
    }

    private static Optional<Silence> silence(List<AiSentOrders.Sent> late) {
        return late.stream().map(AiSentOrders.Sent::sentAt).min(Comparator.naturalOrder())
                .map(since -> new Silence(late.size(), since));
    }

    /** «11/11/2026 14:42», en hora de España. */
    public static String dateTime(Instant instant) {
        return DATE_TIME.format(instant.atZone(ClaudePickups.ZONE));
    }

    /** «las 9:30» si es de hoy, «ayer a las 9:30» o «el 9/11 a las 9:30». */
    public static String since(Instant instant, Instant now) {
        ZonedDateTime at = instant.atZone(ClaudePickups.ZONE);
        var today = now.atZone(ClaudePickups.ZONE).toLocalDate();
        String hour = "las " + ClaudePickups.hour(at);
        if (at.toLocalDate().equals(today)) return hour;
        if (at.toLocalDate().equals(today.minusDays(1))) return "ayer a " + hour;
        return "el " + at.getDayOfMonth() + "/" + at.getMonthValue() + " a " + hour;
    }

    private static List<String> types(boolean onPc) {
        return Arrays.stream(AiRequestType.values()).filter(t -> t.onPc() == onPc).map(Enum::name).toList();
    }
}
