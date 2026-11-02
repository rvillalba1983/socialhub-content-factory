package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.PromotionsApi;
import com.socialhub.api.model.PromotionDto;
import com.socialhub.api.model.PromotionListDto;
import com.socialhub.promotion.PromotionQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PromotionsController implements PromotionsApi {

    private final PromotionQueries queries;
    private final PromotionWebMapper mapper;

    @Override
    public ResponseEntity<PromotionListDto> listPromotions() {
        return ResponseEntity.ok(new PromotionListDto(mapper.toPromotionDtos(queries.promotions())));
    }

    @Override
    public ResponseEntity<PromotionDto> getPromotion(String promotionId) {
        return ResponseEntity.ok(mapper.toDto(queries.promotion(promotionId)));
    }
}
