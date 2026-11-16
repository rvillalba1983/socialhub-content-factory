package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AiRequestJpaRepository extends JpaRepository<AiRequestEntity, String> {

    long countBySentAtGreaterThanEqual(Instant since);

    long countBySentAtGreaterThanEqualAndTypeNotIn(Instant since, Collection<String> types);

    Optional<AiRequestEntity> findFirstByTaskIdAndStatusOrderBySentAtDesc(String taskId, AiRequest.Status status);

    List<AiRequestEntity> findByTaskIdOrderBySentAt(String taskId);

    List<AiRequestEntity> findByStatus(AiRequest.Status status);

    /** Cuándo llegó el último resultado (de cualquier tipo, bueno o malo) de esos tipos de encargo, si ha llegado alguno. */
    @Query("select max(r.finishedAt) from AiRequestEntity r where r.type in :types "
            + "and r.status <> com.socialhub.ai.domain.AiRequest.Status.SENT")
    @Nullable Instant lastFinished(@Param("types") Collection<String> types);

    /** Cerrojo de la transacción actual (se suelta al terminar): dos altas del mismo tipo y promoción no se cruzan. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:key))) l", nativeQuery = true)
    int lockOrders(@Param("key") String key);

    boolean existsByTypeIn(Collection<String> types);
}
