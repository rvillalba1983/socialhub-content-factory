package com.socialhub.ai;

import com.socialhub.production.ProductionService;
import com.socialhub.production.TaskRequest;
import com.socialhub.promotion.PromotionQueries;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta de encargos a Claude. Quedan en cola y salen hacia la carpeta de Drive de la marca. */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class AiOrders {

    private final ProductionService production;
    private final PromotionQueries promotions;
    private final AiSettings settings;
    private final AiHandlers handlers;

    /** @return identificador de la tarea */
    @Transactional
    public String submit(String promotionId, AiRequestType type, String title, String goal, List<String> inputs) {
        if (type.onPc()) throw new IllegalArgumentException("«" + type.label() + "» lo encarga SocialHub al publicar");
        return submitInternal(promotionId, type, title, goal, inputs);
    }

    /** Alta de un encargo desde otro módulo (también los de publicación con Chrome en el PC). */
    @Transactional
    public String submitInternal(String promotionId, AiRequestType type, String title, String goal, List<String> inputs) {
        promotions.promotion(promotionId);
        if (goal.isBlank()) throw new IllegalArgumentException("Falta el objetivo del encargo");
        String name = title.isBlank() ? type.label() : title.strip();
        List<String> clean = inputs.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        handlers.of(type).ifPresent(h -> h.validateOrder(promotionId, clean));
        return production.submitAi(new TaskRequest(type.stationId(), promotionId, type.taskType(), name, goal.strip(),
                clean, List.of()),
                settings.model(type).name());
    }
}
