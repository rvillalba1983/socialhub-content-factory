package com.socialhub.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface BrandDecisionJpaRepository extends JpaRepository<BrandDecisionEntity, Long> {
    List<BrandDecisionEntity> findByBrandIdOrderByCreatedAtAscIdAsc(String brandId);
}
