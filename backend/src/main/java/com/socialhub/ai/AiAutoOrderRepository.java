package com.socialhub.ai;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiAutoOrderRepository extends JpaRepository<AiAutoOrderEntity, AiAutoOrderEntity.Key> {
    List<AiAutoOrderEntity> findByTypeAndWeekStart(String type, LocalDate weekStart);
}
