package com.socialhub.production;

import java.time.Duration;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Sabe ejecutar un tipo de tarea con herramientas locales. Los módulos que aportan trabajo (herramientas, piezas…)
 * registran sus ejecutores como beans y el motor los usa para las tareas en cola de ese tipo.
 *
 * <p>Nunca ejecuta órdenes inventadas: recibe los datos de entrada de la tarea y usa operaciones con parámetros
 * validados.
 */
@SecondaryPort
public interface TaskExecutor {

    /** Tipo de tarea que ejecuta, por ejemplo {@code CHECK_LOCAL_TOOLS}. */
    String type();

    /** Nombre visible del agente local, por ejemplo «FFmpeg · Java 2D». */
    String agentName();

    /** Tiempo máximo. Al agotarse se interrumpe y la tarea queda con error. */
    Duration timeout();

    /**
     * Hace el trabajo. Debe parar en cuanto el hilo se interrumpa (pausa, cancelación o tiempo agotado).
     *
     * @return resultado que verá Raúl
     * @throws Exception el mensaje se guarda como error de la tarea
     */
    String run(Run run) throws Exception;

    /** Lo que el ejecutor sabe de la tarea y cómo informa de su avance. */
    interface Run {
        String taskId();

        @Nullable String promotionId();

        List<String> inputs();

        /** Línea del registro de ejecución, visible en el historial de la tarea. */
        void log(String message);

        /** Avance de 0 a 100. */
        void progress(double percent);
    }
}
