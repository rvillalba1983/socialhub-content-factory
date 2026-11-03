package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PubloraAccountJpaRepository extends JpaRepository<PubloraAccountEntity, PubloraAccountEntity.Key> {
    List<PubloraAccountEntity> findByBrandIdOrderByPlatform(String brandId);

    @Modifying
    @Query("delete from PubloraAccountEntity a where a.brandId = :brandId")
    void deleteByBrand(String brandId);
}
