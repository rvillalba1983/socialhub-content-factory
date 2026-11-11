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
import com.socialhub.api.model.RecommendationDto;
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
    private final com.socialhub.ai.AiHeldOrders heldOrders;
    private final FactoryRecommendations recommendations;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<FactoryDto> getFactory() {
        var snapshot = production.snapshot();
        var armoryItems = armoryMapper.toDtos(armory.items());
        boolean real = mode.selected() == com.socialhub.platform.AppMode.REAL;
        List<RecommendationDto> recs = real ? recommendations.compute() : List.of();
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
                alerts(snapshot.alerts(), armoryItems, publishing.publishAlerts(),
                        real ? heldOrders.current() : List.of(), recs),
                recs));
    }

    /**
     * A los avisos de producción se suman los de publicación (a mano, con error o con posible duplicado) y los servicios
     * reales no disponibles (por ejemplo, Drive sin montar).
     */
    private static List<AlertDto> alerts(List<AlertDto> production, List<ArmoryItemDto> items,
                                         List<PublishingQueries.PublishAlert> publishing,
                                         List<com.socialhub.ai.AiHeldOrders.Held> held,
                                         List<RecommendationDto> recommendations) {
        List<AlertDto> alerts = new ArrayList<>(production);
        // Encargos a Claude que no pueden salir hasta que Raúl arregle algo (por ejemplo, una carpeta)
        for (var h : held) {
            // Las propuestas retenidas de una promoción salen solo en «Qué hacer ahora», que ya no recorta la lista
            if (h.isPromotionProposals() && h.promotionId() != null) continue;
            alerts.add(new AlertDto(AlertDto.KindEnum.BLOCKED, AlertDto.TargetTypeEnum.TASK, h.taskId(), h.stationId(),
                    "En cola: " + h.title(), h.reason()));
        }
        // Lo que impide publicar sale también en «Necesita atención», sacado de las mismas recomendaciones (un solo cálculo)
        for (var r : recommendations) {
            boolean silent = r.getKind() == RecommendationDto.KindEnum.PC_TASK_SILENT;
            if (!silent && r.getKind() != RecommendationDto.KindEnum.NO_PUBLISHING_ROUTE) continue;
            alerts.add(new AlertDto(silent ? AlertDto.KindEnum.FAILED : AlertDto.KindEnum.BLOCKED,
                    AlertDto.TargetTypeEnum.ARMORY, silent ? ServicesWatch.PC_ID : ServicesWatch.PUBLORA_ID, "prog",
                    r.getTitle(), r.getDetail() == null ? "" : r.getDetail()));
        }
        for (var a : publishing) {
            alerts.add(new AlertDto(a.failed() ? AlertDto.KindEnum.FAILED : AlertDto.KindEnum.BLOCKED,
                    AlertDto.TargetTypeEnum.PUBLICATION, a.publicationId(), "pub", a.title(), a.detail()));
        }
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
