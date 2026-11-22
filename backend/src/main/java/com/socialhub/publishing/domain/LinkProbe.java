package com.socialhub.publishing.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Comprueba si un enlace https responde. Nunca llega a direcciones privadas ni locales. */
@SecondaryPort
public interface LinkProbe {

    enum State {
        /** Responde 2xx (tras seguir las redirecciones). */
        OK,
        /** No responde, tarda demasiado o devuelve un error. */
        NOT_RESPONDING,
        /** No se ha comprobado a propósito: no es https o apunta a una dirección privada, local o de metadatos. */
        REFUSED
    }

    State probe(String url);
}
