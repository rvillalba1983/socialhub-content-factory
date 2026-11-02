package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.model.BrandDto;
import com.socialhub.api.model.PromotionDto;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface PromotionWebMapper {

    BrandDto toDto(Brand brand);

    List<BrandDto> toBrandDtos(List<Brand> brands);

    PromotionDto toDto(Promotion promotion);

    List<PromotionDto> toPromotionDtos(List<Promotion> promotions);
}
