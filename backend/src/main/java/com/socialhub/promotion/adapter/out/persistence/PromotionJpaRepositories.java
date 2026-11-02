package com.socialhub.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BrandJpaRepository extends JpaRepository<BrandEntity, String> {
    List<BrandEntity> findAllByOrderByPosition();

    @Query("select coalesce(max(b.position), -1) + 1 from BrandEntity b")
    int nextPosition();
}

interface PromotionJpaRepository extends JpaRepository<PromotionEntity, String> {
    List<PromotionEntity> findAllByOrderByPosition();

    @Query("select coalesce(max(p.position), -1) + 1 from PromotionEntity p")
    int nextPosition();
}

interface CommonRuleJpaRepository extends JpaRepository<CommonRuleEntity, Integer> {
    List<CommonRuleEntity> findAllByOrderByPosition();
}
