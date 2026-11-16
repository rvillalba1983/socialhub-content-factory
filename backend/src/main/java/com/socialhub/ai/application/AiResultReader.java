package com.socialhub.ai.application;

import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.AiResultFile;
import com.socialhub.ai.domain.AiResultFileRepository;
import com.socialhub.production.ProductionService;
import com.socialhub.promotion.PromotionQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cada 30 s, en modo real: lee los resultados nuevos de {@code <Encargos de la marca>/de-claude}, los valida con el
 * contrato y completa, marca con error o pasa a Raúl la tarea que los esperaba. Cada archivo (ruta y fecha) se lee una
 * sola vez; no se mueve ni se borra nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiResultReader {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    /** Un archivo recién escrito puede estar a medio sincronizar: se espera a que lleve unos segundos quieto. */
    static final Duration SETTLE = Duration.ofSeconds(3);

    private final PromotionQueries promotions;
    private final ProductionService production;
    private final AiRequestRepository requests;
    private final AiResultFileRepository files;
    private final ResultValidator validator;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AiHandlers handlers;

    /** @return cuántos archivos nuevos se han leído */
    int readAll() {
        int n = 0;
        for (String brandId : promotions.brandIds()) {
            Optional<String> orders = promotions.brandOrdersFolder(brandId);
            if (orders.isEmpty() || !AiFolders.available(orders.get())) continue;
            Path dir = Path.of(orders.get()).resolve(AiFolders.FROM_CLAUDE);
            if (!Files.isDirectory(dir)) continue;
            for (Path file : list(dir)) {
                Instant modified = modified(file);
                if (modified == null || modified.isAfter(clock.instant().minus(SETTLE))) continue;
                if (files.alreadyRead(file.toString(), modified)) continue;
                try {
                    process(file, modified);
                } catch (RuntimeException e) {
                    log.warn("Resultado {} no procesado: {}", file, e.toString());
                    record(file, modified, null, "INVALID", "Error al procesarlo: " + e.getMessage());
                }
                n++;
            }
        }
        return n;
    }

    private void process(Path file, Instant modified) {
        JsonNode result;
        try {
            if (Files.size(file) > MAX_BYTES) {
                record(file, modified, null, "INVALID", "Pasa de 2 MB");
                return;
            }
            result = json.readTree(Files.readString(file));
        } catch (IOException | JacksonException e) {
            // Puede que aún no esté entero: si cambia su fecha, se vuelve a leer
            record(file, modified, null, "INVALID", "No es un JSON válido: " + e.getMessage());
            return;
        }
        String taskId = result.path("taskId").asString("");
        Optional<AiRequest> request = taskId.isEmpty() ? Optional.empty()
                : requests.latestWaitingOf(taskId);
        List<String> errors = validator.validate(result);
        if (!errors.isEmpty()) {
            String detail = "No cumple el contrato task-result.v1: " + String.join("; ", errors.subList(0, Math.min(5, errors.size())));
            if (request.isPresent() && production.waitingAiTask(taskId).isPresent()) {
                transactions.executeWithoutResult(s -> {
                    failed(request.get(), "Resultado no válido: " + detail);
                    finish(request.get(), AiRequest.Status.INVALID, file, result, detail);
                    production.aiFailed(taskId, "Resultado no válido (" + file.getFileName() + "): " + detail);
                    record(file, modified, request.get().id().value(), "INVALID", detail);
                });
            } else {
                record(file, modified, null, "INVALID", detail);
            }
            return;
        }
        if (request.isEmpty() || production.waitingAiTask(taskId).isEmpty()) {
            record(file, modified, request.map(r -> r.id().value()).orElse(null), "IGNORED",
                    "Ninguna tarea espera este resultado (" + taskId + ")");
            return;
        }
        AiRequest sent = request.get();
        String status = result.path("status").asString();
        AiRequestType type = sent.type();
        transactions.executeWithoutResult(s -> {
            switch (status) {
                case "completed" -> {
                    String applied;
                    try {
                        applied = apply(sent, type, result, file);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        String error = "El resultado no sirve: " + e.getMessage();
                        failed(sent, error);
                        finish(sent, AiRequest.Status.INVALID, file, result, error);
                        production.aiFailed(taskId, error + " (" + file.getFileName() + ")");
                        break;
                    }
                    finish(sent, AiRequest.Status.COMPLETED, file, result, null);
                    production.aiCompleted(taskId, applied);
                }
                case "failed" -> {
                    String error = text(result.path("error"), "Claude no indica el motivo");
                    failed(sent, error);
                    finish(sent, AiRequest.Status.FAILED, file, result, error);
                    production.aiFailed(taskId, error);
                }
                default -> {
                    String reason = text(result.path("error"), String.join(" · ", warnings(result)));
                    if (type.automatic()) failed(sent, "Claude pide ayuda: " + reason);
                    finish(sent, AiRequest.Status.NEEDS_HUMAN, file, result, reason);
                    production.aiNeedsHuman(taskId, reason.isBlank() ? "Claude necesita que lo revises." : reason);
                }
            }
            record(file, modified, sent.id().value(), "APPLIED", status);
        });
        log.info("Resultado de Claude para {}: {} ({})", taskId, status, file);
    }

    /** Avisa al manejador del tipo de que el encargo no salió, para que su módulo siga por otro camino. */
    private void failed(AiRequest sent, String reason) {
        AiRequestType type = sent.type();
        var handler = handlers.of(type);
        if (handler.isEmpty() || sent.promotionId() == null) return;
        production.waitingAiTask(sent.taskId()).ifPresent(task -> handler.get().onFailed(
                new AiJob(task.id(), type, sent.promotionId(), sent.brandId(), task.title(), task.goal(), task.inputs(), task.createdAt(),
                        sent.requestPath()),
                reason));
    }

    /** El manejador del tipo hace lo suyo con el resultado; sin manejador, vale el resumen. */
    private String apply(AiRequest sent, AiRequestType type, JsonNode result, Path file) {
        var handler = handlers.of(type);
        if (handler.isEmpty() || sent.promotionId() == null) return summary(result, file);
        var task = production.waitingAiTask(sent.taskId()).orElseThrow();
        AiJob job = new AiJob(task.id(), type, sent.promotionId(), sent.brandId(), task.title(), task.goal(),
                task.inputs(), task.createdAt(), sent.requestPath());
        String text = handler.get().apply(job, result.path("outputs"), result);
        List<String> warnings = warnings(result);
        return warnings.isEmpty() ? text : text + " · Avisos: " + String.join(" · ", warnings);
    }

    private void finish(AiRequest request, AiRequest.Status status, Path file, JsonNode result,
                        @Nullable String error) {
        Instant now = clock.instant();
        Instant started = instant(result.path("startedAt"));
        Instant finished = instant(result.path("finishedAt"));
        requests.save(request.answered(status, file.toString(), finished != null ? finished : now,
                Duration.between(started != null ? started : request.sentAt(), finished != null ? finished : now).toMillis(),
                result.path("agent").path("runtime").asString(null), result.path("agent").path("model").asString(null),
                error));
    }

    private void record(Path file, Instant modified, @Nullable String requestId, String outcome, @Nullable String note) {
        transactions.executeWithoutResult(s -> files.save(new AiResultFile(file.toString(), modified, requestId,
                outcome, note == null ? null : note.substring(0, Math.min(note.length(), 2000)), clock.instant())));
    }

    static String summary(JsonNode result, Path file) {
        JsonNode outputs = result.path("outputs");
        String text = text(outputs.path("summary"), text(outputs.path("resumen"), "Resultado de Claude: " + file.getFileName()));
        List<String> warnings = warnings(result);
        return warnings.isEmpty() ? text : text + " · Avisos: " + String.join(" · ", warnings);
    }

    private static List<String> warnings(JsonNode result) {
        List<String> list = new ArrayList<>();
        result.path("warnings").forEach(w -> list.add(w.asString()));
        return list;
    }

    private static String text(JsonNode node, String fallback) {
        return node.isString() && !node.asString().isBlank() ? node.asString().strip() : fallback;
    }

    private static @Nullable Instant instant(JsonNode node) {
        if (!node.isString()) return null;
        try {
            return OffsetDateTime.parse(node.asString()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<Path> list(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static @Nullable Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().truncatedTo(ChronoUnit.MILLIS);
        } catch (IOException e) {
            return null;
        }
    }
}
