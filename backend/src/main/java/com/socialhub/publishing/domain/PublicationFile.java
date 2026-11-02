package com.socialhub.publishing.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Un archivo asociado a una publicación.
 *
 * @param origin quién lo asoció: el spec.json al generar las piezas o Raúl a mano
 */
@ValueObject
public record PublicationFile(String path, Origin origin, Instant addedAt) {

    public enum Origin { SPEC, MANUAL }
}
