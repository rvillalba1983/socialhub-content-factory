package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.EditorialMemoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaEditorialMemoryRepository implements EditorialMemoryRepository {

    private final RejectionJpaRepository rejections;
    private final CorrectionJpaRepository corrections;

    @Override
    public void add(Rejection r) {
        rejections.save(new MemoryEntities.RejectionEntity(null, r.brandId(), r.promotionId(), r.publicationId(), r.kind(),
                r.reason(), r.note(), r.title(), r.content(), r.at()));
    }

    @Override
    public void add(Correction c) {
        corrections.save(new MemoryEntities.CorrectionEntity(null, c.brandId(), c.promotionId(), c.publicationId(),
                c.beforeCaption(), c.afterCaption(), c.beforeHashtags(), c.afterHashtags(), c.at()));
    }

    @Override
    public List<Rejection> rejections(String brandId, int limit) {
        return rejections.findByBrandIdOrderByCreatedAtDescIdDesc(brandId, PageRequest.ofSize(limit)).stream()
                .map(e -> new Rejection(e.getBrandId(), e.getPromotionId(), e.getPublicationId(), e.getKind(), e.getReason(),
                        e.getNote(), e.getTitle(), e.getContent(), e.getCreatedAt()))
                .toList();
    }

    @Override
    public List<Correction> corrections(String brandId, int limit) {
        return corrections.findByBrandIdOrderByCreatedAtDescIdDesc(brandId, PageRequest.ofSize(limit)).stream()
                .map(e -> new Correction(e.getBrandId(), e.getPromotionId(), e.getPublicationId(), e.getBeforeCaption(),
                        e.getAfterCaption(), e.getBeforeHashtags(), e.getAfterHashtags(), e.getCreatedAt()))
                .toList();
    }
}
