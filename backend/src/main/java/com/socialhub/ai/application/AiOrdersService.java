package com.socialhub.ai.application;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiSettings;
import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.production.ProductionAiTasks;
import com.socialhub.production.TaskRequest;
import com.socialhub.promotion.PromotionQueries;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AiOrdersService implements AiOrders {

    private final ProductionAiTasks production;
    private final PromotionQueries promotions;
    private final AiSettings settings;
    private final AiHandlers handlers;
    private final AiRequestRepository requests;

    @Override
    @Transactional(readOnly = true)
    public Optional<Boolean> urgentOf(String taskId) {
        List<AiRequest> sent = requests.ofTask(taskId);
        return sent.isEmpty() ? Optional.empty() : Optional.of(sent.getLast().urgent());
    }

    @Override
    @Transactional
    public String submit(String promotionId, AiRequestType type, String title, String goal, List<String> inputs) {
        if (type.automatic()) throw new IllegalArgumentException("«" + type.label() + "» lo encarga SocialHub al publicar");
        String taskId = submitInternal(promotionId, type, title, goal, inputs);
        production.markManual(taskId);
        return taskId;
    }

    @Override
    @Transactional
    public String submitInternal(String promotionId, AiRequestType type, String title, String goal, List<String> inputs) {
        promotions.requirePromotion(promotionId);
        if (goal.isBlank()) throw new IllegalArgumentException("Falta el objetivo del encargo");
        String name = title.isBlank() ? type.label() : title.strip();
        // Un alta a la vez por tipo y promoción: lo que se comprueba abajo (publicaciones ya en otro encargo) no puede
        // cambiar entre comprobar y guardar, ni por un encargo a mano ni por el automático
        if (handlers.of(type).isPresent()) requests.lockOrders(type.name() + ":" + promotionId);
        List<String> clean = inputs.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        List<String> complete = handlers.of(type).map(h -> h.completeInputs(promotionId, clean)).orElse(clean);
        handlers.of(type).ifPresent(h -> h.validateOrder(promotionId, complete));
        return production.submitAi(new TaskRequest(type.stationId(), promotionId, type.taskType(), name, goal.strip(),
                complete, List.of()),
                settings.model(type).name());
    }
}
