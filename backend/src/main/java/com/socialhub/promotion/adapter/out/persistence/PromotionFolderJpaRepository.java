package com.socialhub.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PromotionFolderJpaRepository
        extends JpaRepository<FolderEntities.PromotionFolderEntity, FolderEntities.PromotionFolderId> {
    List<FolderEntities.PromotionFolderEntity> findByPromotionId(String promotionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from com.socialhub.promotion.adapter.out.persistence.FolderEntities$PromotionFolderEntity f where f.promotionId = :promotionId")
    void deleteByPromotion(String promotionId);
}
