package com.socialhub.publishing;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Las rondas periódicas de publicaciones. Cada una trabaja solo en modo real, y un fallo en una ronda se anota y no
 * afecta a las demás.
 */
@PrimaryPort
public interface PublishingRounds {

    /** Mueve las publicaciones: programa, comprueba, publica a su hora y recoge lo que dicen las vías. */
    void publish();

    /** Pide a Claude lo que toque (textos y control de calidad) de las publicaciones que ya lo necesitan. */
    void requestAuto();

    /** Lee el calendario externo (Publer y las redes) y lo contrasta con las publicaciones. */
    void readCalendar();

    /** Pide las métricas de las publicaciones que ya llevan publicadas el tiempo suficiente. */
    void readMetrics();
}
