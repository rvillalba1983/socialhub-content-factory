package com.socialhub.ai.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Dónde se guardan los envíos a Claude. */
@SecondaryPort
public interface AiRequestRepository {

    /** Crea o actualiza. */
    void save(AiRequest request);

    long countSentSince(Instant since);

    /** Envíos desde ese instante, sin contar los de esos tipos. */
    long countSentSinceExcluding(Instant since, Collection<AiRequestType> excluded);

    /** El último envío de la tarea que sigue esperando respuesta. */
    Optional<AiRequest> latestWaitingOf(String taskId);

    /** El último envío de la tarea que SocialHub retiró (puede que Claude lo hiciera igualmente y su resultado llegue tarde). */
    Optional<AiRequest> latestWithdrawnOf(String taskId);

    /** Los envíos de la tarea, del más antiguo al más nuevo. */
    List<AiRequest> ofTask(String taskId);

    List<AiRequest> withStatus(AiRequest.Status status);

    /** Cuándo llegó el último resultado (de cualquier tipo, bueno o malo) de esos tipos de encargo, si ha llegado alguno. */
    @Nullable Instant lastFinished(Collection<AiRequestType> types);

    boolean anyOfTypes(Collection<AiRequestType> types);

    /** Cerrojo de la transacción actual (se suelta al terminar): dos altas del mismo tipo y promoción no se cruzan. */
    void lockOrders(String key);

    /** Página de envíos, los más recientes primero. */
    AiRequestPage page(int offset, int limit);
}
