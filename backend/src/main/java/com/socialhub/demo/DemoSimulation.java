package com.socialhub.demo;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** El simulador del modo demo: hace avanzar las tareas de ejemplo para que el anillo se mueva. */
@PrimaryPort
public interface DemoSimulation {

    /** Un paso: siempre sobre la base de datos del modo demo, aunque la interfaz muestre el modo real. */
    void tick();
}
