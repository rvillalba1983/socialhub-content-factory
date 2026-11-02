package com.socialhub.ai;

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
import com.socialhub.platform.OffsetLimit;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class AiController implements ClaudeApi {

    private final AiSettings settings;
    private final AiOrders orders;
    private final AiRequestRepository requests;

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
    @Transactional(readOnly = true)
    public ResponseEntity<AiRequestPageDto> listAiRequests(Integer offset, Integer limit) {
        var page = requests.findAll(new OffsetLimit(offset, limit, Sort.by(Sort.Direction.DESC, "sentAt", "id")));
        return ResponseEntity.ok(new AiRequestPageDto(page.map(AiController::toDto).getContent(), offset, limit,
                page.getTotalElements()));
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
                        AiModelDto.valueOf(e.getValue().name())))
                .toList();
        return new AiSettingsDto(view.dailyLimit(), view.usedToday(), models);
    }

    private static AiRequestDto toDto(AiRequestEntity e) {
        AiRequestDto dto = new AiRequestDto(e.getId(), e.getTaskId(), e.getBrandId(), AiRequestTypeDto.valueOf(e.getType()),
                AiModelDto.valueOf(e.getModel().name()), e.getAttempt(), AiRequestDto.StatusEnum.valueOf(e.getStatus().name()),
                e.getRequestPath(), at(e.getSentAt()));
        dto.setPromotionId(e.getPromotionId());
        dto.setResultPath(e.getResultPath());
        dto.setFinishedAt(e.getFinishedAt() == null ? null : at(e.getFinishedAt()));
        dto.setDurationMs(e.getDurationMs());
        dto.setRuntime(e.getRuntime());
        dto.setResultModel(e.getResultModel());
        dto.setError(e.getError());
        return dto;
    }

    private static OffsetDateTime at(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }

}
