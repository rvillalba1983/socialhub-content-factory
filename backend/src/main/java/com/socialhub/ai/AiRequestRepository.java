package com.socialhub.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiRequestRepository extends JpaRepository<AiRequestEntity, String> {
    long countBySentAtGreaterThanEqual(Instant since);

    long countBySentAtGreaterThanEqualAndTypeNotIn(Instant since, java.util.Collection<String> types);

    Optional<AiRequestEntity> findFirstByTaskIdAndStatusOrderBySentAtDesc(String taskId, AiRequestEntity.Status status);

    List<AiRequestEntity> findByTaskIdOrderBySentAt(String taskId);

    List<AiRequestEntity> findByStatus(AiRequestEntity.Status status);

    /** Cuándo llegó el último resultado (de cualquier tipo, bueno o malo) de esos tipos de encargo, si ha llegado alguno. */
    @org.springframework.data.jpa.repository.Query("select max(r.finishedAt) from AiRequestEntity r where r.type in :types "
            + "and r.status <> com.socialhub.ai.AiRequestEntity.Status.SENT")
    @org.jspecify.annotations.Nullable Instant lastFinished(@org.springframework.data.repository.query.Param("types") java.util.Collection<String> types);

    boolean existsByTypeIn(java.util.Collection<String> types);

    Page<AiRequestEntity> findAllByOrderBySentAtDesc(Pageable page);
}
