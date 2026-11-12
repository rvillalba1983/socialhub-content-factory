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
 * (nunca de urgente a no urgente). La marca guardada en la base de datos no cambia: es la que se usa para saber cuándo
 * tocaba recogerlo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiUrgency {

    private final AiRequestRepository requests;
    private final ObjectMapper json;
    private final Clock clock;

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
            Path file = Path.of(request.getRequestPath());
            try {
                if (!Files.isRegularFile(file)) continue;
                JsonNode tree = json.readTree(Files.readString(file));
                if (!(tree instanceof ObjectNode root) || root.path("urgent").asBoolean(false)) continue;
                @org.jspecify.annotations.Nullable Instant deadline = deadline(root);
                boolean onPc = AiRequestType.valueOf(request.getType()).onPc();
                if (!ClaudePasses.urgent(onPc, deadline, now)) continue;
                root.put("urgent", true);
                Path partial = file.resolveSibling("." + file.getFileName() + ".part");
                Files.writeString(partial, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                log.info("Encargo {} marcado como urgente: ya no llega a la pasada de la noche", request.getTaskId());
                changed++;
            } catch (IOException | RuntimeException e) {
                log.debug("No se ha podido recalcular la urgencia de {}: {}", file, e.toString());
            }
        }
        return changed;
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
