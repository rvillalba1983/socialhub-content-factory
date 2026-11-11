package com.socialhub.ai;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.production.AiTask;
import com.socialhub.production.ProductionService;
import com.socialhub.promotion.PromotionQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Cada 30 s, en modo real: los encargos a Claude en cola salen hacia {@code <Encargos de la marca>/para-claude},
 * por orden de llegada y sin pasar del límite diario. Los dos primeros intentos usan el modelo de la tarea; el tercero,
 * un nivel más arriba.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiDispatcher {

    private final ProductionService production;
    private final PromotionQueries promotions;
    private final AiSettings settings;
    private final AiRequestRepository requests;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AiHandlers handlers;
    private final java.util.List<AiContextProvider> contexts;
    private final AiHeldOrders held;
    private final ApplicationEventPublisher events;
    /** Encargos de esta ronda que esperan a Raúl. */
    private java.util.Map<String, AiHeldOrders.Held> holding = AiHeldOrders.collector();

    @Scheduled(initialDelayString = "${socialhub.ai.initial-delay-millis:10000}",
               fixedDelayString = "${socialhub.ai.dispatch-millis:30000}")
    void dispatch() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                dispatchQueued();
            } catch (RuntimeException e) {
                log.warn("Envío de encargos a Claude omitido: {}", e.toString());
            }
        });
    }

    /** @return cuántos encargos han salido */
    int dispatchQueued() {
        int sent = 0;
        holding = AiHeldOrders.collector();
        for (AiTask task : production.queuedAiTasks()) {
            try {
                if (dispatch(task)) sent++;
            } catch (RuntimeException e) {
                log.warn("El encargo {} no ha podido salir: {}", task.id(), e.toString());
                hold(task, "No se ha podido dejar en Drive: " + e.getMessage()
                        + ". Se vuelve a intentar en el siguiente envío.");
            }
        }
        // Solo si cambia lo retenido, para no despertar a la pantalla cada 30 s sin motivo
        if (held.replace(holding)) events.publishEvent(new FactoryChanged("ai", null));
        return sent;
    }

    private boolean dispatch(AiTask task) {
        Optional<AiRequestType> type = AiRequestType.ofTaskType(task.type());
        if (type.isEmpty()) {
            hold(task, "Tipo de encargo desconocido: " + task.type());
            return false;
        }
        if (task.promotionId() == null) {
            hold(task, "El encargo no tiene promoción: no se sabe a qué marca enviarlo.");
            return false;
        }
        String brandId = promotions.brandOf(task.promotionId());
        Optional<String> orders = promotions.brandOrdersFolder(brandId);
        if (orders.isEmpty()) {
            hold(task, "La marca no tiene carpeta de «Encargos». Indícala o créala en Marcas → Carpetas de Drive.");
            return false;
        }
        if (!AiFolders.available(orders.get())) {
            production.blockQueued(task.id(), "La carpeta de «Encargos» (" + orders.get() + ") no está disponible en este "
                    + "ordenador. ¿Está abierto Google Drive para ordenador? Saldrá sola cuando aparezca.", orders.get());
            return false;
        }
        int limit = settings.dailyLimit();
        if (!type.get().onPc() && settings.usedToday() >= limit) {
            production.keepQueued(task.id(), "Límite diario de encargos a Claude alcanzado (" + limit
                    + "). Sale mañana, o sube el límite en «Claude».");
            return false;
        }

        ClaudeModel current = ClaudeModel.valueOf(task.model());
        ClaudeModel model = task.attempt() >= 3 ? ClaudeModel.max(current, settings.model(type.get()).up()) : current;
        String requestId = UUID.randomUUID().toString();
        Path file = write(orders.get(), task, type.get(), brandId, model, requestId);
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            AiRequestEntity request = new AiRequestEntity();
            request.setId(requestId);
            request.setTaskId(task.id());
            request.setBrandId(brandId);
            request.setPromotionId(task.promotionId());
            request.setType(type.get().name());
            request.setModel(model);
            request.setAttempt(task.attempt());
            request.setStatus(AiRequestEntity.Status.SENT);
            request.setRequestPath(file.toString());
            request.setSentAt(now);
            requests.save(request);
            production.delegateToAi(task.id(), model.name(), "Encargada a Claude " + model.label()
                    + " (intento " + task.attempt() + "): espera su resultado en «" + AiFolders.FROM_CLAUDE + "».");
        });
        log.info("Encargo {} ({}) enviado a Claude {}: {}", task.id(), type.get(), model.label(), file);
        return true;
    }

    /** Sigue en cola por algo que tiene que arreglar Raúl: queda anotado en la tarea y sale en «Necesita atención». */
    private void hold(AiTask task, String reason) {
        production.keepQueued(task.id(), reason);
        Optional<AiRequestType> type = AiRequestType.ofTaskType(task.type());
        String station = type.map(AiRequestType::stationId).orElse("gen");
        holding.put(task.id(), new AiHeldOrders.Held(task.id(), station, task.title(), reason, task.promotionId(),
                type.orElse(null)));
    }

    /** Escribe el encargo con otro nombre y lo renombra al final, para que nadie lea un archivo a medias. */
    private Path write(String ordersFolder, AiTask task, AiRequestType type, String brandId, ClaudeModel model,
                       String requestId) {
        try {
            Path dir = Files.createDirectories(Path.of(ordersFolder).resolve(type.onPc() ? AiFolders.TO_CLAUDE_PC : AiFolders.TO_CLAUDE));
            Files.createDirectories(Path.of(ordersFolder).resolve(AiFolders.FROM_CLAUDE));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("contract", "socialhub.task-request");
            request.put("version", 1);
            request.put("taskId", task.id());
            request.put("brand", brandId);
            request.put("promotion", task.promotionId());
            request.put("type", type.contractType());
            request.put("goal", task.goal());
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("title", task.title());
            inputs.put("items", task.inputs());
            AiJob job = new AiJob(task.id(), type, java.util.Objects.requireNonNull(task.promotionId()), brandId,
                    task.title(), task.goal(), task.inputs(), task.createdAt());
            Map<String, Object> identity = promotions.identityOf(task.promotionId()).forAi();
            if (!identity.isEmpty()) inputs.put("brand_identity", identity);
            contexts.forEach(c -> inputs.putAll(c.context(task.promotionId(), brandId)));
            handlers.of(type).ifPresent(h -> inputs.putAll(h.inputs(job)));
            request.put("inputs", inputs);
            request.put("rules", promotions.rulesFor(task.promotionId()));
            request.put("model", model.name().toLowerCase(java.util.Locale.ROOT));
            handlers.of(type).map(h -> h.deadline(job)).ifPresent(d -> request.put("deadline", d.toString()));
            request.put("idempotencyKey", requestId);
            Path target = fresh(dir, task.id() + "-" + task.attempt());
            Path partial = dir.resolve("." + target.getFileName() + ".part");
            Files.writeString(partial, json.writerWithDefaultPrettyPrinter().writeValueAsString(request));
            return Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Path fresh(Path dir, String name) {
        Path candidate = dir.resolve(name + ".json");
        for (int n = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); n++) {
            candidate = dir.resolve(name + "_" + n + ".json");
        }
        return candidate;
    }
}
