package com.socialhub.ai.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiAutoOrderJpaRepository extends JpaRepository<AiAutoOrderEntity, AiAutoOrderEntity.Key> {
    List<AiAutoOrderEntity> findByPromotionIdAndType(String promotionId, String type);

    List<AiAutoOrderEntity> findByTypeAndWeekStart(String type, LocalDate weekStart);
}
