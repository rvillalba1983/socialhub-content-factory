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

    Page<AiRequestEntity> findAllByOrderBySentAtDesc(Pageable page);
}
