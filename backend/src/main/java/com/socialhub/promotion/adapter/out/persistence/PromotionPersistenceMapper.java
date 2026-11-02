package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface PromotionPersistenceMapper {
    Brand toDomain(BrandEntity entity);

    Promotion toDomain(PromotionEntity entity);

    Promotion.Milestone toDomain(PromotionParts.MilestoneRow row);

    Promotion.Resource toDomain(PromotionParts.ResourceRow row);

    Promotion.Automation toDomain(PromotionParts.AutomationRow row);

    Promotion.Route toDomain(PromotionParts.RouteRow row);

    Promotion.Setting toDomain(PromotionParts.SettingRow row);

    @Mapping(target = "position", ignore = true)
    void update(Brand brand, @MappingTarget BrandEntity entity);

    @Mapping(target = "position", ignore = true)
    void update(Promotion promotion, @MappingTarget PromotionEntity entity);

    PromotionParts.MilestoneRow toRow(Promotion.Milestone milestone);

    PromotionParts.ResourceRow toRow(Promotion.Resource resource);

    PromotionParts.AutomationRow toRow(Promotion.Automation automation);

    PromotionParts.RouteRow toRow(Promotion.Route route);

    PromotionParts.SettingRow toRow(Promotion.Setting setting);
}
