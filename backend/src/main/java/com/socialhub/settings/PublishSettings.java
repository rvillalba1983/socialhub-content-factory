package com.socialhub.settings;

import java.time.Duration;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Margen mínimo de publicación: la única fuente de «cuánto antes de su hora tiene que quedar una publicación para poder
 * programarla». Por defecto 30 minutos, ajustable entre 5 minutos y 24 horas. Nunca se publica al instante.
 */
@PrimaryPort
public interface PublishSettings {

    int DEFAULT_MINUTES = 30;
    int MIN_MINUTES = 5;
    int MAX_MINUTES = 1440;

    record View(int minMarginMinutes, int defaultMinutes, int minMinutes, int maxMinutes) {}

    /** El margen vigente. */
    Duration minMargin();

    View view();

    /** Cambia el margen. Fuera de 5 a 1440 minutos es un error de validación. */
    View update(int minutes);
}
