package com.socialhub.factory;

import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.ClaudeActivity;
import com.socialhub.armory.ArmoryService;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PubloraService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada minuto, en modo real, deja en «Agentes y herramientas» tres servicios deducidos de lo que SocialHub ya sabe (sin
 * llamadas externas): «Claude en la nube», «Claude en el PC · Publer» y «Publora». Solo se guarda cuando cambia algo.
 * El estado {@code WAITING} es el de aviso suave (no sale en «Necesita atención»); {@code FAILED}, el de algo parado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ServicesWatch {

    static final String CLOUD_ID = "claude-cloud";
    static final String PC_ID = "claude-pc";
    static final String PUBLORA_ID = "publora";

    private final ArmoryService armory;
    private final AiSentOrders sentOrders;
    private final ClaudeActivity activity;
    private final PubloraService publora;
    private final PromotionQueries promotions;
    private final Clock clock;
    private final java.util.Map<String, String> last = new java.util.concurrent.ConcurrentHashMap<>();

    record Result(String status, String note) {}

    @Scheduled(initialDelayString = "${socialhub.services-watch.initial-delay-millis:8000}",
               fixedDelayString = "${socialhub.services-watch.millis:60000}")
    void check() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                refresh();
            } catch (RuntimeException e) {
                log.warn("Comprobación de Claude y Publora omitida: {}", e.toString());
            }
        });
    }

    void refresh() {
        Instant now = clock.instant();
        List<AiSentOrders.Sent> sent = sentOrders.waiting();
        register(CLOUD_ID, "Claude en la nube",
                "Tarea programada «Encargos de SocialHub»: recoge los encargos de para-claude y deja su resultado en de-claude.",
                cloud(activity.lastResult(false), activity.everSent(false), sent, now), List.of("Propuestas", "Textos", "Especificación"));
        register(PC_ID, "Claude en el PC · Publer",
                "Tarea programada «Publicar con Chrome» del PC: hace los encargos de para-claude-pc (programar en Publer, comprobar, métricas).",
                pc(activity.lastResult(true), activity.everSent(true), sent, now), List.of("Programar en Publer", "Comprobar publicación", "Métricas"));
        register(PUBLORA_ID, "Publora",
                "Segunda vía de publicación (API de Publora). La clave vive en la carpeta de datos de SocialHub.",
                publora(publora.hasKey(), !promotions.brandRefs().isEmpty()
                        && promotions.brandRefs().stream().allMatch(b -> publora.isDismissed(b.id()))), List.of("Publicar"));
    }

    private void register(String id, String name, String description, Result result, List<String> capabilities) {
        String key = result.status() + "|" + result.note();
        if (Objects.equals(key, last.get(id))) return;
        armory.registerService(id, name, description, result.status(), result.note(), capabilities);
        last.put(id, key);
    }

    static Result cloud(Optional<Instant> lastResult, boolean everSent, List<AiSentOrders.Sent> sent, Instant now) {
        var silence = ClaudeActivity.cloudSilence(sent, now);
        if (silence.isPresent()) {
            return new Result("WAITING", silence.get().count() + (silence.get().count() == 1 ? " encargo" : " encargos")
                    + " sin resultado desde " + ClaudeActivity.since(silence.get().since(), now)
                    + ". Revisa la tarea programada «Encargos de SocialHub».");
        }
        if (lastResult.isPresent()) return new Result("OK", "Último resultado: " + ClaudeActivity.dateTime(lastResult.get()));
        if (everSent || ClaudeActivity.anyWaiting(sent, false)) return new Result("WAITING", "Esperando el primer resultado");
        return new Result("IDLE", "Sin encargos todavía");
    }

    static Result pc(Optional<Instant> lastResult, boolean everSent, List<AiSentOrders.Sent> sent, Instant now) {
        var silence = ClaudeActivity.pcSilence(sent, now);
        if (silence.isPresent()) return new Result("FAILED", FactoryRecommendations.pcDetail(silence.get(), now));
        if (lastResult.isPresent()) return new Result("OK", "Último resultado: " + ClaudeActivity.dateTime(lastResult.get()));
        if (everSent || ClaudeActivity.anyWaiting(sent, true)) return new Result("WAITING", "Esperando el primer resultado");
        return new Result("IDLE", "Sin encargos todavía");
    }

    static Result publora(boolean hasKey, boolean allBrandsDismissed) {
        if (hasKey) return new Result("OK", "Conectada");
        if (allBrandsDismissed) return new Result("IDLE", "No se usa");
        return new Result("WAITING", "Sin clave");
    }
}
