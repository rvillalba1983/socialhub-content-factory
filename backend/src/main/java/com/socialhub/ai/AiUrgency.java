package com.socialhub.ai;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Un encargo que sigue esperando en Drive puede volverse urgente con el paso del tiempo: se escribió cuando aún podía
 * esperar a la pasada de la noche, esa pasada ya no le llega a tiempo y la siguiente es de las que solo hacen los
 * urgentes. Cada pocos minutos se recalcula el campo {@code urgent} de los archivos pendientes y se reescribe el que cambia
 * (nunca de urgente a no urgente) y se actualiza también la marca de la base de datos, que es la que usan los avisos de
 * retraso. Solo mira los tipos con «deadline» y los de contenido (una publicación que entra en las próximas 48 h los sube a urgentes) y no toca un archivo hasta pasado el margen de la última pasada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiUrgency {

    private final AiRequestRepository requests;
    private final ObjectMapper json;
    private final Clock clock;
    private final AiHandlers handlers;
    private final com.socialhub.production.ProductionService production;
    private final AiUrgentRule rule;

    @Scheduled(initialDelayString = "${socialhub.ai.urgency-initial-delay-millis:90000}",
               fixedDelayString = "${socialhub.ai.urgency-millis:300000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                refresh();
            } catch (RuntimeException e) {
                log.warn("Recálculo de la urgencia de los encargos omitido: {}", e.toString());
            }
        });
    }

    /** @return cuántos archivos de encargo han pasado a urgentes */
    int refresh() {
        Instant now = clock.instant();
        int changed = 0;
        for (AiRequestEntity request : requests.findByStatus(AiRequestEntity.Status.SENT)) {
            if (request.isUrgent()) continue;
            AiRequestType type = AiRequestType.valueOf(request.getType());
            // Sin «deadline» ni publicaciones de las que dependa nunca se vuelve urgente: ni se lee el archivo
            if (!type.urgencyCanRise()) continue;
            // Justo después de una pasada Claude puede estar leyendo el archivo: no se reescribe hasta pasado el margen
            Instant lastPass = ClaudePasses.lastAtOrBefore(type.onPc(), now);
            if (now.isBefore(lastPass.plus(type.onPc() ? PcSchedule.LATE_AFTER : ClaudePickups.GRACE))) continue;
            Path file = Path.of(request.getRequestPath());
            try {
                if (!Files.isRegularFile(file)) continue;
                JsonNode tree = json.readTree(Files.readString(file));
                if (!(tree instanceof ObjectNode root) || root.path("urgent").asBoolean(false)) continue;
                @org.jspecify.annotations.Nullable Instant deadline = deadline(root);
                // O vence antes de la pasada de la noche, o una de sus publicaciones ha entrado en las próximas 48 h
                if (!ClaudePasses.urgent(type.onPc(), deadline, ClaudePickups.visibleAt(now))
                        && !rule.nearContent(contentDue(request, type), now)) continue;
                root.put("urgent", true);
                Path partial = file.resolveSibling("." + file.getFileName() + ".part");
                Files.writeString(partial, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                // La pasada que tocaba ahora es otra: los avisos de retraso tienen que contar con la nueva marca
                request.setUrgent(true);
                requests.save(request);
                log.info("Encargo {} marcado como urgente: ya no llega a la pasada de la noche", request.getTaskId());
                changed++;
            } catch (IOException | RuntimeException e) {
                log.debug("No se ha podido recalcular la urgencia de {}: {}", file, e.toString());
            }
        }
        return changed;
    }

    /** La hora de la publicación más próxima de las que dependen del encargo, si es de contenido y sigue esperando. */
    private @org.jspecify.annotations.Nullable Instant contentDue(AiRequestEntity request, AiRequestType type) {
        if (!type.dependsOnPublications()) return null;
        return production.waitingAiTask(request.getTaskId()).flatMap(task -> handlers.of(type).map(h -> h.contentDueAt(
                new AiJob(task.id(), type, java.util.Objects.requireNonNull(task.promotionId()), request.getBrandId(),
                        task.title(), task.goal(), task.inputs(), task.createdAt())))).orElse(null);
    }

    private static @org.jspecify.annotations.Nullable Instant deadline(ObjectNode root) {
        JsonNode node = root.path("deadline");
        if (!node.isString()) return null;
        try {
            return Instant.parse(node.asString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
