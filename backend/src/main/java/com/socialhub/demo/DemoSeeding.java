package com.socialhub.demo;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Carga de los datos de ejemplo del modo demo. */
@PrimaryPort
public interface DemoSeeding {

    /** Carga los datos de ejemplo la primera vez que la base de datos del modo demo está vacía. Nunca toca la del modo real. */
    void seedIfEmpty();
}
