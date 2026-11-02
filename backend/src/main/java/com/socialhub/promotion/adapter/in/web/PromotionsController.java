package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.PromotionsApi;
import com.socialhub.api.model.PromotionDto;
import com.socialhub.api.model.PromotionInputDto;
import com.socialhub.api.model.PromotionListDto;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionService;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PromotionsController implements PromotionsApi {

    private final PromotionQueries queries;
    private final PromotionService service;
    private final PromotionWebMapper mapper;

    @Override
    public ResponseEntity<PromotionListDto> listPromotions() {
        return ResponseEntity.ok(new PromotionListDto(mapper.toPromotionDtos(queries.promotions())));
    }

    @Override
    public ResponseEntity<PromotionDto> getPromotion(String promotionId) {
        return ResponseEntity.ok(mapper.toDto(queries.promotion(promotionId)));
    }

    @Override
    public ResponseEntity<PromotionDto> createPromotion(String requestGuard, PromotionInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(service.createPromotion(mapper.toDraft(input))));
    }

    @Override
    public ResponseEntity<PromotionDto> updatePromotion(String promotionId, String requestGuard, PromotionInputDto input) {
        return ResponseEntity.ok(mapper.toDto(service.updatePromotion(promotionId, mapper.toDraft(input))));
    }
}
