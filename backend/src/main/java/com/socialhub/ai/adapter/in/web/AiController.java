package com.socialhub.ai.adapter.in.web;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequests;
import com.socialhub.ai.AiSettings;
import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudeModel;
import com.socialhub.api.ClaudeApi;
import com.socialhub.api.model.AiModelDto;
import com.socialhub.api.model.AiOrderDto;
import com.socialhub.api.model.AiOrderInputDto;
import com.socialhub.api.model.AiRequestDto;
import com.socialhub.api.model.AiRequestPageDto;
import com.socialhub.api.model.AiRequestTypeDto;
import com.socialhub.api.model.AiSettingsDto;
import com.socialhub.api.model.AiSettingsInputDto;
import com.socialhub.api.model.AiSettingsInputModelsInnerDto;
import com.socialhub.api.model.AiTypeModelDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class AiController implements ClaudeApi {

    private final AiSettings settings;
    private final AiOrders orders;
    private final AiRequests requests;

    @Override
    public ResponseEntity<AiSettingsDto> getAiSettings() {
        return ResponseEntity.ok(toDto(settings.view()));
    }

    @Override
    public ResponseEntity<AiSettingsDto> updateAiSettings(String requestGuard, AiSettingsInputDto input) {
        Map<AiRequestType, ClaudeModel> models = new EnumMap<>(AiRequestType.class);
        for (AiSettingsInputModelsInnerDto m : input.getModels()) {
            if (models.put(AiRequestType.valueOf(m.getType().name()), ClaudeModel.valueOf(m.getModel().name())) != null) {
                throw new IllegalArgumentException("El tipo " + m.getType() + " aparece dos veces");
            }
        }
        return ResponseEntity.ok(toDto(settings.update(input.getDailyLimit(), models)));
    }

    @Override
    public ResponseEntity<AiRequestPageDto> listAiRequests(Integer offset, Integer limit) {
        var page = requests.page(offset, limit);
        return ResponseEntity.ok(new AiRequestPageDto(page.items().stream().map(AiController::toDto).toList(), offset, limit,
                page.total()));
    }

    @Override
    public ResponseEntity<AiOrderDto> createAiOrder(String promotionId, String requestGuard, AiOrderInputDto input) {
        String taskId = orders.submit(promotionId, AiRequestType.valueOf(input.getType().name()),
                input.getTitle() == null ? "" : input.getTitle(), input.getGoal(),
                input.getInputs() == null ? List.of() : input.getInputs());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AiOrderDto(taskId));
    }

    private static AiSettingsDto toDto(AiSettings.View view) {
        List<AiTypeModelDto> models = view.models().entrySet().stream()
                .map(e -> new AiTypeModelDto(AiRequestTypeDto.valueOf(e.getKey().name()), e.getKey().label(),
                        AiModelDto.valueOf(e.getValue().name()), e.getKey().onPc()))
                .toList();
        models.forEach(m -> m.setAutomatic(AiRequestType.valueOf(m.getType().name()).automatic()));
        return new AiSettingsDto(view.dailyLimit(), view.usedToday(), models);
    }

    private static AiRequestDto toDto(AiRequest e) {
        AiRequestDto dto = new AiRequestDto(e.id().value(), e.taskId(), e.brandId(), AiRequestTypeDto.valueOf(e.type().name()),
                AiModelDto.valueOf(e.model().name()), e.attempt(), AiRequestDto.StatusEnum.valueOf(e.status().name()),
                e.requestPath(), at(e.sentAt()));
        dto.setPromotionId(e.promotionId());
        dto.setResultPath(e.resultPath());
        dto.setFinishedAt(e.finishedAt() == null ? null : at(e.finishedAt()));
        dto.setDurationMs(e.durationMs());
        dto.setRuntime(e.runtime());
        dto.setResultModel(e.resultModel());
        dto.setError(e.error());
        return dto;
    }

    private static OffsetDateTime at(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
}
