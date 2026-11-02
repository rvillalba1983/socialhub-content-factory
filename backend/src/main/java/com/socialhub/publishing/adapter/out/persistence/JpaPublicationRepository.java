package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.platform.OffsetLimit;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFilter;
import com.socialhub.publishing.domain.PublicationPage;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPublicationRepository implements PublicationRepository {

    private final PublicationJpaRepository jpa;
    private final PublicationPersistenceMapper mapper;

    @Override
    public PublicationPage find(PublicationFilter filter, long offset, int limit) {
        Specification<PublicationEntity> spec =
                (root, query, cb) -> cb.equal(root.get("promotionId"), filter.promotionId());
        if (!filter.statuses().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(filter.statuses()));
        }
        if (filter.platform() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("platform"), filter.platform()));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduledOn"), filter.from()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("scheduledOn"), filter.to()));
        }
        if (filter.flagged() != null) {
            spec = spec.and((root, query, cb) -> filter.flagged()
                    ? cb.isNotNull(root.get("flag")) : cb.isNull(root.get("flag")));
        }
        var page = jpa.findAll(spec, new OffsetLimit(offset, limit, PublicationJpaRepository.CALENDAR));
        return new PublicationPage(page.map(mapper::toDomain).getContent(), offset, limit, page.getTotalElements());
    }

    @Override
    public List<Publication> findAll() {
        return jpa.findAll(PublicationJpaRepository.CALENDAR).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Publication> findByPromotionAndStatus(String promotionId, PublicationStatus status) {
        return jpa.findByPromotionIdAndStatus(promotionId, status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Publication> findFlagged(String promotionId) {
        return jpa.findByPromotionIdAndFlagIsNotNull(promotionId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Publication> findById(String id) { return jpa.findById(id).map(mapper::toDomain); }

    @Override
    public boolean exists(String id) { return jpa.existsById(id); }

    @Override
    public Publication save(Publication publication) {
        var existing = jpa.findById(publication.getId());
        PublicationEntity entity = existing.orElseGet(PublicationEntity::new);
        mapper.update(publication, entity);
        // Una entidad ya cargada se guarda sola al confirmar la transacción (sin merge)
        if (existing.isEmpty()) jpa.save(entity);
        return publication;
    }
}
