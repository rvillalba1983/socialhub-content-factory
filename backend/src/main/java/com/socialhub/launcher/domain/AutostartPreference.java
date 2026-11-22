package com.socialhub.launcher.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Lo que Raúl eligió sobre abrirlo con el sistema. Vacío mientras no ha elegido (por defecto, activado). */
@SecondaryPort
public interface AutostartPreference {

    Optional<Boolean> get();

    void set(boolean enabled);
}
