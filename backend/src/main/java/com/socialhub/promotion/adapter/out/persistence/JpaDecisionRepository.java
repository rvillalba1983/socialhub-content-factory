package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.EditorialDecision;
import com.socialhub.promotion.domain.DecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaDecisionRepository implements DecisionRepository {

    private final BrandDecisionJpaRepository jpa;

    @Override
    public List<EditorialDecision> decisions(String brandId) {
        return jpa.findByBrandIdOrderByCreatedAtAscIdAsc(brandId).stream().map(JpaDecisionRepository::toDomain).toList();
    }

    @Override
    public EditorialDecision add(String brandId, String text, Instant at) {
        return toDomain(jpa.save(new BrandDecisionEntity(brandId, text, at)));
    }

    @Override
    public boolean delete(String brandId, long id) {
        return jpa.findById(id).filter(d -> d.getBrandId().equals(brandId)).map(d -> {
            jpa.delete(d);
            return true;
        }).orElse(false);
    }

    private static EditorialDecision toDomain(BrandDecisionEntity e) {
        return new EditorialDecision(Objects.requireNonNull(e.getId()), e.getText(), e.getCreatedAt());
    }
}
