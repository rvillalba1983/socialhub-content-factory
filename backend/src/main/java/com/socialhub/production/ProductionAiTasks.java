package com.socialhub.production;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Los encargos a Claude como tareas: lo que el orquestador de IA crea, envía, recoge y reintenta. */
@PrimaryPort
public interface ProductionAiTasks {

    /** Encargo a Claude bloqueado esperando una carpeta, con la ruta que espera. */
    record BlockedAi(AiTask task, String waitingFor) {}

    /** Cómo ocupa un encargo a Claude sus datos de entrada. */
    enum Cover {
        /** Sigue vivo: en cola, en curso, en espera, bloqueado o fallido con reintentos por delante. */
        OPEN,
        /** Ya se completó. */
        DONE,
        /** Falló del todo (sin reintentos): no ocupa nada, pero se sabe cuándo falló. */
        FAILED
    }

    /** Un encargo a Claude, cómo cubre sus datos y cuándo cambió de estado por última vez. */
    record Covering(AiTask task, Cover cover, Instant at, @Nullable String result) {}

    /** Encargo a Claude fallido que aún tiene intentos, con cuándo falló. */
    record RetryableAi(String id, String type, Instant failedAt) {}

    /** Crea un encargo a Claude: tiene 3 intentos (el tercero sube un nivel de modelo). */
    String submitAi(TaskRequest request, String model);

    /** Marca un encargo a Claude recién creado como pedido por Raúl desde la interfaz. */
    void markManual(String taskId);

    /** Cancela un encargo a Claude en cola que ya no tiene nada que hacer. */
    void cancelAi(String taskId);

    /** Encargos a Claude en cola, por orden de llegada. */
    List<AiTask> queuedAiTasks();

    /** Encargos a Claude bloqueados porque les falta una carpeta (salen solos cuando aparece). */
    List<BlockedAi> blockedAiTasks();

    /** Encargos a Claude de un tipo y promoción en cola, vivos, completados o fallidos del todo (no los cancelados). */
    List<Covering> coveringAiTasks(String type, String promotionId);

    List<RetryableAi> retryableAiTasks();

    /** Reintento automático de un encargo a Claude. */
    void autoRetry(String taskId);

    /** El encargo que espera un resultado de Claude, si lo hay con ese identificador. */
    Optional<AiTask> waitingAiTask(String taskId);

    void delegateToAi(String taskId, String model, String reason);

    void keepQueued(String taskId, String reason);

    /** Un encargo en cola no puede salir porque falta su carpeta: se bloquea sin gastar intento. */
    void blockQueued(String taskId, String reason, String path);

    void aiCompleted(String taskId, String result);

    void aiFailed(String taskId, String error);

    default void aiNeedsHuman(String taskId, String reason) { aiNeedsHuman(taskId, reason, List.of()); }

    /** Claude pide a Raúl que decida algo; {@code options} son las respuestas cortas que propone (puede no haber). */
    void aiNeedsHuman(String taskId, String reason, List<String> options);
}
