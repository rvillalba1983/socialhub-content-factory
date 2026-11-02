package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface RejectionJpaRepository extends JpaRepository<MemoryEntities.RejectionEntity, Long> {
    List<MemoryEntities.RejectionEntity> findByBrandIdOrderByCreatedAtDescIdDesc(String brandId, Pageable page);
}
