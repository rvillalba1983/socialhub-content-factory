package com.socialhub.brandkit.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BrandKitJpaRepository extends JpaRepository<BrandKitEntity, Long> {

    Optional<BrandKitEntity> findFirstByBrandIdAndStatus(String brandId, String status);

    @Query(value = "select count(*) > 0 from brand where id = ?1", nativeQuery = true)
    boolean brandExists(String brandId);

    @Query("select coalesce(max(k.version), 0) from BrandKitEntity k where k.brandId = ?1")
    int maxVersion(String brandId);
}
