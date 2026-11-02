package com.socialhub.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface BrandJpaRepository extends JpaRepository<BrandEntity, String> {
    List<BrandEntity> findAllByOrderByPosition();
}

interface PromotionJpaRepository extends JpaRepository<PromotionEntity, String> {
    List<PromotionEntity> findAllByOrderByPosition();
}

interface CommonRuleJpaRepository extends JpaRepository<CommonRuleEntity, Integer> {
    List<CommonRuleEntity> findAllByOrderByPosition();
}
