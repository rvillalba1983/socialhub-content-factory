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
    @Transactional(readOnly = true)
    public Optional<String> promotionOf(String taskId) {
        return requests.ofTask(taskId).stream().map(AiRequest::promotionId).filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    @Transactional
    public int supersede(String promotionId, AiRequestType type, String inputId, @org.jspecify.annotations.Nullable String exceptTaskId) {
        int cancelled = 0;
        for (var cover : production.coveringAiTasks(type.taskType(), promotionId)) {
            var task = cover.task();
            if (cover.cover() != ProductionAiTasks.Cover.OPEN || task.id().equals(exceptTaskId) || !task.inputs().contains(inputId)) continue;
            List<AiRequest> sent = requests.ofTask(task.id());
            // Un encargo que Claude ya ha empezado o contestado no se toca: su resultado se avisará como tardío
            if (!sent.isEmpty() && !withdraw(sent.getLast())) continue;
            production.cancelAi(task.id());
            cancelled++;
        }
        return cancelled;
    }

    /** Retira el archivo de un encargo que sigue en Drive sin resultado. {@code false} si ya no se puede retirar con seguridad. */
    private boolean withdraw(AiRequest request) {
        if (request.status() != AiRequest.Status.SENT) return true;
        java.nio.file.Path file = java.nio.file.Path.of(request.requestPath());
        // Sin la carpeta a la vista no se sabe si Claude ya lo cogió: no se toca
        if (!java.nio.file.Files.isDirectory(file.getParent())) return false;
        try {
            java.nio.file.Path results = file.getParent().resolveSibling(AiFolders.FROM_CLAUDE);
            String base = file.getFileName().toString().replaceAll("\\.json$", "");
            if (java.nio.file.Files.isDirectory(results)) {
                try (var list = java.nio.file.Files.list(results)) {
                    if (list.anyMatch(f -> f.getFileName().toString().startsWith(base))) return false;
                }
            }
            java.nio.file.Files.deleteIfExists(file);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
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
