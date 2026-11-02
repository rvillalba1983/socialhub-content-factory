package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorrectionJpaRepository extends JpaRepository<MemoryEntities.CorrectionEntity, Long> {
    List<MemoryEntities.CorrectionEntity> findByBrandIdOrderByCreatedAtDescIdDesc(String brandId, Pageable page);
}
