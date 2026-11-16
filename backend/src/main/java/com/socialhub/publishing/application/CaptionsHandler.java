package com.socialhub.publishing.application;

import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Textos y hashtags: el encargo lleva las publicaciones (sus identificadores son los datos de entrada) y Claude
 * devuelve {@code outputs.publications: [{id, caption, hashtags}]}, que se guardan en cada publicación.
 */
@Component
@RequiredArgsConstructor
class CaptionsHandler implements AiTypeHandler {

    static final int MAX_CAPTION = 2200;
    static final int MAX_HASHTAGS = 1000;

    private final PublishingQueriesService queries;
    private final PublishingService service;

    @Override public AiRequestType type() { return AiRequestType.CAPTIONS; }

    @Override
    public java.time.@org.jspecify.annotations.Nullable Instant contentDueAt(AiJob job) {
        return queries.earliestScheduled(job.inputs()).orElse(null);
    }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.isEmpty()) throw new IllegalArgumentException("Indica las publicaciones (sus identificadores, uno por línea)");
        for (String id : inputs) {
            Publication p = queries.find(id).filter(x -> x.getPromotionId().equals(promotionId))
                    .orElseThrow(() -> new IllegalArgumentException("La publicación «" + id + "» no existe en esta promoción"));
            if (p.getStatus() == PublicationStatus.SCHEDULED || p.getStatus() == PublicationStatus.PUBLISHING
                    || p.getStatus() == PublicationStatus.PUBLISHED) {
                throw new IllegalArgumentException("La publicación «" + id + "» ya está programada o publicada: no se le cambia el texto");
            }
        }
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : job.inputs()) {
            queries.find(id).ifPresent(p -> list.add(PublishingQueriesService.forAi(p)));
        }
        return Map.of("publications", list,
                "expected_outputs", "publications: [{id, caption (máx. 2200), hashtags (máx. 1000)}] y summary");
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode items = outputs.path("publications");
        if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("Faltan los textos («outputs.publications»)");
        Set<String> asked = new HashSet<>(job.inputs());
        List<PublishingService.Texts> texts = new ArrayList<>();
        for (JsonNode item : items) {
            String id = item.path("id").asString("");
            String caption = item.path("caption").asString("").strip();
            String hashtags = item.path("hashtags").asString("").strip();
            if (!asked.contains(id)) throw new IllegalArgumentException("Texto para una publicación que no se pidió: «" + id + "»");
            if (caption.isEmpty()) throw new IllegalArgumentException("Falta el texto de «" + id + "»");
            if (caption.length() > MAX_CAPTION) throw new IllegalArgumentException("El texto de «" + id + "» pasa de 2.200 caracteres");
            if (hashtags.length() > MAX_HASHTAGS) throw new IllegalArgumentException("Los hashtags de «" + id + "» son demasiado largos");
            texts.add(new PublishingService.Texts(id, caption, hashtags));
        }
        // Se comprueba antes de guardar, para no fallar dentro de la transacción del servicio
        validateOrder(job.promotionId(), texts.stream().map(PublishingService.Texts::publicationId).toList());
        int kept = service.applyTexts(job.promotionId(), texts).size();
        int saved = texts.size() - kept;
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return (saved == 0 ? "Ningún texto guardado" : saved + (saved == 1 ? " texto guardado" : " textos guardados") + " para revisar")
                + (kept == 0 ? "" : " · " + kept + (kept == 1 ? " no se aplicó" : " no se aplicaron")
                        + " porque ya tenían texto tuyo (queda en la incidencia de la publicación)")
                + (summary.isEmpty() ? "" : " · " + summary);
    }
}
