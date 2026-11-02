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
import com.socialhub.api.model.AlertDto;
import com.socialhub.api.model.ArmoryItemDto;
import com.socialhub.api.model.StatusLevelDto;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
        var armoryItems = armoryMapper.toDtos(armory.items());
        return ResponseEntity.ok(new FactoryDto(
                OffsetDateTime.now(clock),
                mode.isDemo(),
                promotionMapper.toBrandDtos(promotions.brands()),
                promotionMapper.toPromotionDtos(promotions.promotions()),
                snapshot.stations(),
                snapshot.tasks(),
                publicationMapper.toDtos(publishing.all()),
                armoryItems,
                promotions.commonRules(),
                alerts(snapshot.alerts(), armoryItems)));
    }

    /** A los avisos de producción se suman los servicios reales no disponibles (por ejemplo, Drive sin montar). */
    private static List<AlertDto> alerts(List<AlertDto> production, List<ArmoryItemDto> items) {
        List<AlertDto> alerts = new ArrayList<>(production);
        for (ArmoryItemDto item : items) {
            if (!item.getDemo() && item.getKind() == ArmoryItemDto.KindEnum.SERVICE
                    && item.getStatus() == StatusLevelDto.BLOCKED) {
                alerts.add(new AlertDto(AlertDto.KindEnum.BLOCKED, AlertDto.TargetTypeEnum.ARMORY, item.getId(), "gen",
                        item.getName(), item.getNote()));
            }
        }
        return alerts;
    }
}
