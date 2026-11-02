package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.FolderRepository;
import com.socialhub.promotion.domain.FolderRole;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaFolderRepository implements FolderRepository {

    private final BrandFolderJpaRepository brands;
    private final PromotionFolderJpaRepository promotions;

    @Override
    public Map<FolderRole, String> brandFolders(String brandId) {
        Map<FolderRole, String> map = new EnumMap<>(FolderRole.class);
        brands.findByBrandId(brandId).forEach(f -> map.put(f.getRole(), f.getLocation()));
        return map;
    }

    @Override
    public Map<FolderRole, String> promotionFolders(String promotionId) {
        Map<FolderRole, String> map = new EnumMap<>(FolderRole.class);
        promotions.findByPromotionId(promotionId).forEach(f -> map.put(f.getRole(), f.getLocation()));
        return map;
    }

    @Override
    public void replaceBrandFolders(String brandId, Map<FolderRole, String> folders) {
        brands.deleteByBrand(brandId);
        folders.forEach((role, location) -> brands.save(new FolderEntities.BrandFolderEntity(brandId, role, location)));
    }

    @Override
    public void replacePromotionFolders(String promotionId, Map<FolderRole, String> folders) {
        promotions.deleteByPromotion(promotionId);
        folders.forEach((role, location) ->
                promotions.save(new FolderEntities.PromotionFolderEntity(promotionId, role, location)));
    }
}
