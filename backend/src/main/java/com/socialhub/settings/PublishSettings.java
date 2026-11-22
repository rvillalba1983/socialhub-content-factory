package com.socialhub.settings;

import java.time.Duration;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Márgenes mínimos de publicación: la única fuente de «cuánto antes de su hora tiene que quedar una publicación para
 * poder programarla». Hay dos ajustes: el general (por defecto 30 minutos) y el de TikTok (por defecto 8 horas, porque
 * TikTok se publica por Publer, que programa Claude con Chrome en sus pasadas). Los dos van de 5 minutos a 24 horas.
 * Nunca se publica al instante. El margen de una publicación depende de su red: {@link #minMarginFor(String)}.
 */
@PrimaryPort
public interface PublishSettings {

    int DEFAULT_MINUTES = 30;
    int TIKTOK_DEFAULT_MINUTES = 480;
    int MIN_MINUTES = 5;
    int MAX_MINUTES = 1440;

    /** Nombre de la red TikTok (el de {@code Platform.TIKTOK}); este módulo no depende de «publishing». */
    String TIKTOK = "TIKTOK";

    record View(int minMarginMinutes, int tiktokMinMarginMinutes, int defaultMinutes, int tiktokDefaultMinutes,
                int minMinutes, int maxMinutes) {}

    /**
     * El margen vigente para una red, por su nombre ({@code Platform.name()}): TIKTOK usa el de TikTok y las demás el
     * general. Es lo que hay que usar donde se valida o se calcula por publicación.
     */
    Duration minMarginFor(String platform);

    /** El margen general (el de las redes que no son TikTok), para lo que no es de una red concreta. */
    default Duration minMargin() { return minMarginFor(""); }

    View view();

    /**
     * Cambia los márgenes; {@code tiktokMinutes} a {@code null} deja el de TikTok como estaba. Fuera de 5 a 1440 minutos
     * es un error de validación y no se guarda nada.
     */
    View update(int minutes, @Nullable Integer tiktokMinutes);

    /** Cambia solo el margen general. */
    default View update(int minutes) { return update(minutes, null); }
}
