package com.socialhub.factory.adapter.in.web;

import com.socialhub.api.FactoryApi;
import com.socialhub.api.model.FactoryDto;
import com.socialhub.armory.adapter.in.web.ArmoryView;
import com.socialhub.factory.FactoryAttention;
import com.socialhub.factory.domain.Attention;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.adapter.in.web.ProductionView;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.adapter.in.web.PromotionView;
import com.socialhub.publishing.adapter.in.web.PublicationView;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** La vista completa del anillo: compone las vistas de los demás módulos y lo que necesita atención. */
@RestController
@RequiredArgsConstructor
class FactoryController implements FactoryApi {

    private final PromotionQueries promotions;
    private final PromotionView promotionView;
    private final ProductionView production;
    private final PublicationView publications;
    private final ArmoryView armory;
    private final FactoryAttention attention;
    private final FactoryWebMapper mapper;
    private final AppModeHolder mode;
    private final Clock clock;

    @Override
    public ResponseEntity<FactoryDto> getFactory() {
        var snapshot = production.snapshot();
        Attention needed = attention.attention(snapshot.alerts());
        return ResponseEntity.ok(new FactoryDto(
                OffsetDateTime.now(clock),
                mode.isDemo(),
                promotionView.brands(),
                promotionView.promotions(),
                snapshot.stations(),
                snapshot.tasks(),
                publications.all(),
                armory.items(),
                promotions.commonRules(),
                mapper.toAlertDtos(needed.alerts()),
                mapper.toRecommendationDtos(needed.recommendations())));
    }
}
