package com.socialhub.brandkit.adapter.out.persistence;

import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.BrandKitRepository;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.platform.DomainException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
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
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException e) {
            // Otro cambio sobre este kit se coló entre leerlo y guardarlo (por ejemplo, llegó un kit nuevo que lo sustituyó)
            throw new DomainException("El kit ha cambiado mientras se guardaba (otra aprobación o un kit nuevo a la vez); recarga e inténtalo de nuevo.");
        }
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
