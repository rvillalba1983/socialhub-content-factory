package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Platform;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PublerAccountJpaRepository extends JpaRepository<PublerAccountEntity, PublerAccountEntity.Key> {
    List<PublerAccountEntity> findByBrandIdOrderByPlatform(String brandId);

    @Query("select a from PublerAccountEntity a where a.platform = :platform and lower(trim(a.accountName)) = lower(trim(:name))")
    List<PublerAccountEntity> using(String name, Platform platform);

    @Modifying
    @Query("delete from PublerAccountEntity a where a.brandId = :brandId")
    void deleteByBrand(String brandId);
}
