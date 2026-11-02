package com.socialhub.platform.live;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Avisos en directo al navegador (Server-Sent Events). */
@RestController
@Component
public class LiveUpdates {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping("/api/events")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter, "hello", Map.of());
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FactoryChanged change) {
        Map<String, String> payload = change.id() == null
                ? Map.of("kind", change.kind())
                : Map.of("kind", change.kind(), "id", change.id());
        emitters.forEach(e -> send(e, "factory-changed", payload));
    }

    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emitters.forEach(e -> send(e, "ping", Map.of()));
    }

    public int subscribers() { return emitters.size(); }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }
}
