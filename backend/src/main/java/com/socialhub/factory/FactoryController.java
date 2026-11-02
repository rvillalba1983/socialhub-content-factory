package com.socialhub.factory;

import com.socialhub.api.FactoryApi;
import com.socialhub.api.model.FactoryDto;
import com.socialhub.armory.ArmoryQueries;
import com.socialhub.armory.adapter.in.web.ArmoryWebMapper;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.adapter.in.web.ProductionView;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.adapter.in.web.PromotionWebMapper;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.adapter.in.web.PublicationWebMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class FactoryController implements FactoryApi {

    private final PromotionQueries promotions;
    private final PromotionWebMapper promotionMapper;
    private final ProductionView production;
    private final PublishingQueries publishing;
    private final PublicationWebMapper publicationMapper;
    private final ArmoryQueries armory;
    private final ArmoryWebMapper armoryMapper;
    private final AppModeHolder mode;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<FactoryDto> getFactory() {
        var snapshot = production.snapshot();
        return ResponseEntity.ok(new FactoryDto(
                OffsetDateTime.now(clock),
                mode.isDemo(),
                promotionMapper.toBrandDtos(promotions.brands()),
                promotionMapper.toPromotionDtos(promotions.promotions()),
                snapshot.stations(),
                snapshot.tasks(),
                publicationMapper.toDtos(publishing.all()),
                armoryMapper.toDtos(armory.items()),
                promotions.commonRules(),
                snapshot.alerts()));
    }
}
