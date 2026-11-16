package com.socialhub.production;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Las rondas periódicas del motor de tareas del modo real. Un fallo en una ronda se anota y no afecta a las demás. */
@PrimaryPort
public interface TaskRounds {

    /** Recupera lo que quedó en curso tras un cierre (la primera vez) y toma de la cola lo que toca ejecutar. */
    void poll();

    /** Las tareas que esperaban una carpeta vuelven a la cola cuando la carpeta aparece. */
    void requeueWhenFoldersAppear();
}
