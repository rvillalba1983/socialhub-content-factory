package com.socialhub.platform;

import java.time.Duration;

/** Una duración con palabras para los textos de la interfaz: «30 minutos», «1 hora», «6 horas», «1 h 30 min». */
public final class SpanText {

    private SpanText() {}

    public static String of(Duration span) {
        long minutes = span.toMinutes();
        if (minutes < 60) return minutes + (minutes == 1 ? " minuto" : " minutos");
        long hours = minutes / 60, rest = minutes % 60;
        if (rest == 0) return hours + (hours == 1 ? " hora" : " horas");
        return hours + " h " + rest + " min";
    }
}
