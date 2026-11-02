package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPromotionRepository implements PromotionRepository {

    private final BrandJpaRepository brands;
    private final PromotionJpaRepository promotions;
    private final CommonRuleJpaRepository rules;
    private final PromotionPersistenceMapper mapper;

    @Override
    public List<Brand> brands() {
        return brands.findAllByOrderByPosition().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Promotion> promotions() {
        return promotions.findAllByOrderByPosition().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Promotion> promotion(String id) {
        return promotions.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<String> commonRules() {
        return rules.findAllByOrderByPosition().stream().map(CommonRuleEntity::getText).toList();
    }
}
