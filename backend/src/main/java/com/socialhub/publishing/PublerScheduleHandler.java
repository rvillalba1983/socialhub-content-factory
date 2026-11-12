package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublerAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * «Programar en Publer»: Claude, con Chrome en el PC de Raúl, programa la publicación en la web de Publer y devuelve
 * {@code outputs.scheduled = true}, la hora con la que quedó y, si lo ve, el identificador de Publer.
 */
@Component
class PublerScheduleHandler implements AiTypeHandler {

    /** Diferencia admitida entre la hora pedida y la que dice Claude que quedó. */
    static final Duration TOLERANCE = Duration.ofMinutes(1);

    private final PublishingQueries queries;
    private final PublicationFileRepository files;
    private final PublishingEngine engine;
    private final PublerAccountRepository accounts;

    PublerScheduleHandler(PublishingQueries queries, PublicationFileRepository files, @Lazy PublishingEngine engine,
                          PublerAccountRepository accounts) {
        this.queries = queries;
        this.files = files;
        this.engine = engine;
        this.accounts = accounts;
    }

    @Override public AiRequestType type() { return AiRequestType.PUBLER_SCHEDULE; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.size() != 1 || queries.find(inputs.getFirst()).filter(p -> p.getPromotionId().equals(promotionId)).isEmpty()) {
            throw new IllegalArgumentException("El encargo lleva una publicación de la promoción");
        }
    }

    static Map<String, Object> publication(Publication p, PublicationFileRepository files) {
        var m = new java.util.LinkedHashMap<>(PublishingQueries.forAi(p));
        m.put("scheduled_at", p.scheduledAt().toString());
        m.put("timezone", p.getTimezone());
        m.put("files", files.files(p.getId()).stream().map(f -> f.path()).toList());
        return m;
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        Publication p = queries.find(job.inputs().getFirst()).orElseThrow();
        String account = accounts.account(job.brandId(), p.getPlatform()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publication", publication(p, files));
        m.put("publer_account", account);
        List<String> how = new ArrayList<>();
        if (account == null) {
            how.add("La marca no tiene indicada su cuenta de Publer para " + p.getPlatform() + ": no programes nada y devuelve status failed con ese motivo.");
        } else {
            how.add("Abre Publer (publer.com) en Chrome con la sesión de Raúl; no inicies sesión por él ni crees cuentas.");
            how.add("Crea una publicación " + inAccount(account) + " (" + p.getPlatform() + ") con el texto («caption»), los hashtags y los archivos de «files», en ese orden.");
            how.add("Prográmala para «scheduled_at» (hora de " + p.getTimezone() + "). No la publiques al momento.");
            if (p.getPlatform() == Platform.TIKTOK) how.addAll(tiktokOptions());
            how.add("Antes de terminar, comprueba en el calendario de Publer, en esa cuenta, que aparece a esa hora y no está duplicada.");
            how.add("Si pasó la hora límite («deadline») o falta algo, no programes nada: devuelve status failed con el motivo.");
        }
        m.put("how", how);
        m.put("expected_outputs", "scheduled (true), scheduled_for (ISO 8601 con zona), publer_id si se ve, url si la hay, summary");
        return m;
    }

    /** «en la cuenta «X» de Publer». */
    static String inAccount(String account) { return "en la cuenta «" + account + "» de Publer"; }

    /** Opciones de TikTok al programar en Publer (cuenta personal, públicas desde la primera). */
    static List<String> tiktokOptions() {
        return List.of(
                "TikTok, privacidad: «Todo el mundo» (pública desde la primera publicación; no «Solo yo» ni «Amigos»).",
                "TikTok, interacción: deja activados Comentarios, Dúo y Stitch.",
                "TikTok, sonido: los sonidos ya van en el vídeo; no añadas música ni sonidos de la biblioteca de TikTok.");
    }

    @Override
    public @Nullable Instant deadline(AiJob job) {
        return queries.find(job.inputs().getFirst()).map(p -> PublerRoute.deadline(p.scheduledAt())).orElse(null);
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        String id = job.inputs().getFirst();
        Publication p = queries.find(id).orElseThrow(() -> new IllegalArgumentException("Ya no existe la publicación " + id));
        if (!outputs.path("scheduled").asBoolean(false)) {
            throw new IllegalArgumentException("Falta «outputs.scheduled: true»: si no se pudo programar, el estado es failed");
        }
        String when = outputs.path("scheduled_for").asString("");
        if (when.isBlank()) throw new IllegalArgumentException("Falta «outputs.scheduled_for» (la hora con la que quedó en Publer)");
        Instant at;
        try {
            at = java.time.OffsetDateTime.parse(when).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("«scheduled_for» no es una fecha ISO 8601 con zona: " + when);
        }
        if (Duration.between(at, p.scheduledAt()).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException("Quedó programada a otra hora (" + when + "; tocaba " + p.scheduledAt()
                    + "). Revisa Publer y corrígela allí");
        }
        String publerId = outputs.path("publer_id").asString("");
        String url = outputs.path("url").asString("");
        return engine.scheduled(id, job.taskId(), publerId.isBlank() ? null : publerId, url.isBlank() ? null : url);
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        engine.routeFailed(job.inputs().getFirst(), job.taskId(), reason);
    }
}
