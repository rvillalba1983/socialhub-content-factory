package com.socialhub.production;

import java.util.random.RandomGenerator;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Lo que otros módulos piden al motor de tareas: crear tareas locales y saber si han terminado. */
@PrimaryPort
public interface ProductionTasks {

    /** Crea una tarea. Si depende de otras sin terminar, queda bloqueada hasta que terminen. Devuelve su identificador. */
    String submitTask(TaskRequest request, String agentName);

    /**
     * Tarea de identificador fijo (por ejemplo, una comprobación periódica): si ya existe y no está en marcha,
     * vuelve a la cola; si no existe, se crea.
     */
    void submitOrRequeue(String id, TaskRequest request, String agentName);

    /** La tarea ya terminó (completada, fallida o cancelada), no está en cola ni en curso. Una que no existe cuenta como terminada. */
    boolean isFinished(String taskId);

    /**
     * Un paso del motor de la demo: avanza lo que está en curso y arranca lo que está en cola en estaciones que
     * aceptan trabajo. Solo lo usa el simulador del modo demo.
     */
    int engineTick(RandomGenerator random);
}
