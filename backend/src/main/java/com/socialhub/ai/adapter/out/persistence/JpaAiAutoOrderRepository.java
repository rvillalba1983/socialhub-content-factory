package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiAutoOrder;
import com.socialhub.ai.domain.AiAutoOrderRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaAiAutoOrderRepository implements AiAutoOrderRepository {

    private final AiAutoOrderJpaRepository jpa;

    @Override
    public boolean exists(AiAutoOrder.Key key) { return jpa.existsById(AiAutoOrderEntity.keyOf(key)); }

    @Override
    public void save(AiAutoOrder order) { jpa.save(AiAutoOrderEntity.of(order)); }

    @Override
    public List<AiAutoOrder> ofPromotionAndType(String promotionId, String type) {
        return jpa.findByPromotionIdAndType(promotionId, type).stream().map(AiAutoOrderEntity::toDomain).toList();
    }

    @Override
    public List<AiAutoOrder> ofTypeAndWeek(String type, LocalDate weekStart) {
        return jpa.findByTypeAndWeekStart(type, weekStart).stream().map(AiAutoOrderEntity::toDomain).toList();
    }
}
