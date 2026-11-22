package com.socialhub.updates.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Dónde se publican las versiones (GitHub Releases). */
@SecondaryPort
public interface ReleaseSource {

    /** No se pudo saber (sin red, límite de GitHub, respuesta rara). Nunca lleva datos del usuario. */
    class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }

    /** Las releases publicadas (sin borradores), en cualquier orden. Vacío si no hay ninguna. */
    List<Release> releases();
}
