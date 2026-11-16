package com.socialhub.ai;

import java.time.LocalDate;
import java.util.Set;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Promociones que SocialHub ha pasado solo de «Planificada» a «Activa». Si Raúl la activa a mano, no hay anotación. */
@PrimaryPort
public interface AiAutoStarted {

    /** Las promociones que se activaron solas ese día (hora de España). */
    Set<String> on(LocalDate day);
}
