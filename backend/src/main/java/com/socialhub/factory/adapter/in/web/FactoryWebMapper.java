package com.socialhub.factory.adapter.in.web;

import com.socialhub.api.model.AlertActionDto;
import com.socialhub.api.model.AlertDto;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.api.model.RecommendationTargetDto;
import com.socialhub.factory.domain.FactoryAlert;
import com.socialhub.factory.domain.Recommendation;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traducción entre lo que necesita atención (dominio de la fábrica) y los modelos de la API. */
@Component
class FactoryWebMapper {

    List<AlertDto> toAlertDtos(List<FactoryAlert> alerts) { return alerts.stream().map(this::toDto).toList(); }

    List<RecommendationDto> toRecommendationDtos(List<Recommendation> recommendations) {
        return recommendations.stream().map(this::toDto).toList();
    }

    AlertDto toDto(FactoryAlert alert) {
        var dto = new AlertDto(AlertDto.KindEnum.valueOf(alert.kind().name()),
                AlertDto.TargetTypeEnum.valueOf(alert.targetType().name()), alert.targetId(), alert.stationId(),
                alert.title(), alert.detail());
        if (alert.action() != null) {
            dto.setAction(new AlertActionDto(AlertActionDto.TypeEnum.PUBLER_TO_PUBLORA, alert.action().label(),
                    alert.action().accountName(), PlatformDto.valueOf(alert.action().platform())));
        }
        return dto;
    }

    RecommendationDto toDto(Recommendation recommendation) {
        var dto = new RecommendationDto(recommendation.id(), RecommendationDto.KindEnum.valueOf(recommendation.kind().name()),
                recommendation.title());
        dto.setDetail(recommendation.detail());
        dto.setPromotionId(recommendation.promotionId());
        dto.setPublicationIds(recommendation.publicationIds());
        dto.setEtaText(recommendation.etaText());
        if (recommendation.target() != null) {
            var target = new RecommendationTargetDto(RecommendationTargetDto.ScreenEnum.valueOf(recommendation.target().screen().name()));
            target.setBrandId(recommendation.target().brandId());
            target.setPromotionId(recommendation.target().promotionId());
            dto.setTarget(target);
        }
        return dto;
    }
}
