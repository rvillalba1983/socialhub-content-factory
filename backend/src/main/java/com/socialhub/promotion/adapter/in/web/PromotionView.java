package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.model.BrandDto;
import com.socialhub.api.model.PromotionDto;
import com.socialhub.promotion.PromotionQueries;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vista de marcas y promociones para la API. La usa la vista completa del anillo ({@code /api/factory}): así no
 * necesita tipos de dominio de este módulo, solo los DTO del contrato.
 */
@Component
@RequiredArgsConstructor
public class PromotionView {

    private final PromotionQueries queries;
    private final PromotionWebMapper mapper;

    @Transactional(readOnly = true)
    public List<BrandDto> brands() { return mapper.toBrandDtos(queries.brands()); }

    @Transactional(readOnly = true)
    public List<PromotionDto> promotions() { return mapper.toPromotionDtos(queries.promotions()); }
}
