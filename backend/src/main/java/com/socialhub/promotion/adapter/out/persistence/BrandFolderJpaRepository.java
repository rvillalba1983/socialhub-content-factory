package com.socialhub.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BrandFolderJpaRepository extends JpaRepository<FolderEntities.BrandFolderEntity, FolderEntities.BrandFolderId> {
    List<FolderEntities.BrandFolderEntity> findByBrandId(String brandId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from com.socialhub.promotion.adapter.out.persistence.FolderEntities$BrandFolderEntity f where f.brandId = :brandId")
    void deleteByBrand(String brandId);
}
