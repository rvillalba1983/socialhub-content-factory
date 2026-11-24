package com.socialhub.brandkit.adapter.out.persistence;

import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.BrandKitRepository;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaBrandKitRepository implements BrandKitRepository {

    private final BrandKitJpaRepository jpa;
    private final KitCodec codec;

    @Override
    public BrandKit save(BrandKit kit) {
        BrandKitEntity entity = kit.id() == null ? new BrandKitEntity() : jpa.findById(kit.id()).orElseGet(BrandKitEntity::new);
        entity.setBrandId(kit.brandId());
        entity.setVersion(kit.version());
        entity.setStatus(kit.status().name());
        entity.setKitJson(kit.kitJson());
        entity.setComment(kit.comment());
        entity.setSourceTaskId(kit.sourceTaskId());
        entity.setCreatedAt(kit.createdAt());
        entity.setApprovedAt(kit.approvedAt());
        // flush: el índice único parcial exige que el aprobado anterior ya esté sustituido antes de aprobar el nuevo
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<BrandKit> find(long id) { return jpa.findById(id).map(this::toDomain); }

    @Override
    public Optional<BrandKit> findByStatus(String brandId, KitStatus status) {
        return jpa.findFirstByBrandIdAndStatus(brandId, status.name()).map(this::toDomain);
    }

    @Override
    public boolean brandExists(String brandId) { return jpa.brandExists(brandId); }

    @Override
    public int nextVersion(String brandId) { return jpa.maxVersion(brandId) + 1; }

    private BrandKit toDomain(BrandKitEntity e) {
        return new BrandKit(e.getId(), e.getBrandId(), e.getVersion(), KitStatus.valueOf(e.getStatus()), codec.read(e.getKitJson()),
                e.getKitJson(), e.getComment(), e.getSourceTaskId(), e.getCreatedAt(), e.getApprovedAt());
    }
}
