package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import org.mapstruct.Mapper;

@Mapper
interface PromotionPersistenceMapper {
    Brand toDomain(BrandEntity entity);

    Promotion toDomain(PromotionEntity entity);

    Promotion.Milestone toDomain(PromotionParts.MilestoneRow row);

    Promotion.Resource toDomain(PromotionParts.ResourceRow row);

    Promotion.Automation toDomain(PromotionParts.AutomationRow row);

    Promotion.Route toDomain(PromotionParts.RouteRow row);

    Promotion.Setting toDomain(PromotionParts.SettingRow row);
}
