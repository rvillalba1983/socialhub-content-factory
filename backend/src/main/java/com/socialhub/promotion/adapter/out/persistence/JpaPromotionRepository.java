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
    public Optional<Brand> brand(String id) {
        return brands.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean brandIdTaken(String id) { return brands.existsById(id); }

    @Override
    public boolean promotionIdTaken(String id) { return promotions.existsById(id); }

    @Override
    public Brand save(Brand brand) {
        var existing = brands.findById(brand.id());
        BrandEntity entity = existing.orElseGet(() -> {
            BrandEntity created = new BrandEntity();
            created.setPosition(brands.nextPosition());
            return created;
        });
        mapper.update(brand, entity);
        if (existing.isEmpty()) brands.save(entity);
        return brand;
    }

    @Override
    public Promotion save(Promotion promotion) {
        var existing = promotions.findById(promotion.id());
        PromotionEntity entity = existing.orElseGet(() -> {
            PromotionEntity created = new PromotionEntity();
            created.setPosition(promotions.nextPosition());
            return created;
        });
        mapper.update(promotion, entity);
        if (existing.isEmpty()) promotions.save(entity);
        return promotion;
    }

    @Override
    public List<String> commonRules() {
        return rules.findAllByOrderByPosition().stream().map(CommonRuleEntity::getText).toList();
    }
}
