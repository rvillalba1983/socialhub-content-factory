package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublerAccountRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * «Comprobar publicación»: pasada la hora, Claude mira en Publer y en el perfil de la red si ya está publicada.
 * Devuelve {@code outputs.published} y, si lo está, el enlace o el identificador: sin eso no pasa a «Publicada».
 */
@Component
class PublishCheckHandler implements AiTypeHandler {

    private final PublishingQueries queries;
    private final PublicationFileRepository files;
    private final PublishingEngine engine;
    private final PublerAccountRepository accounts;

    PublishCheckHandler(PublishingQueries queries, PublicationFileRepository files, PublishingEngine engine,
                        PublerAccountRepository accounts) {
        this.queries = queries;
        this.files = files;
        this.engine = engine;
        this.accounts = accounts;
    }

    @Override public AiRequestType type() { return AiRequestType.PUBLISH_CHECK; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.size() != 1 || queries.find(inputs.getFirst()).filter(p -> p.getPromotionId().equals(promotionId)).isEmpty()) {
            throw new IllegalArgumentException("El encargo lleva una publicación de la promoción");
        }
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        Publication p = queries.find(job.inputs().getFirst()).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publication", PublerScheduleHandler.publication(p, files));
        engine.job(p.getId()).ifPresent(j -> {
            if (j.getRoute() != null) m.put("route", j.getRoute());
            if (j.getExternalId() != null) m.put("external_id", j.getExternalId());
        });
        String account = accounts.account(job.brandId(), p.getPlatform()).orElse(null);
        m.put("publer_account", account);
        m.put("how", List.of(
                "Mira en Publer (publicadas)" + (account == null ? "" : ", " + PublerScheduleHandler.inAccount(account) + ",")
                        + " y en el perfil de " + p.getPlatform() + " de la marca si la publicación ya está publicada.",
                "Solo cuenta como publicada si la ves: deja su enlace («url») y, si se ve, su identificador. No supongas nada.",
                "Si no la encuentras, devuelve published false y explica en «note» qué has mirado."));
        m.put("expected_outputs", "published (true/false), url, external_id, note, summary");
        return m;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        String id = job.inputs().getFirst();
        if (queries.find(id).isEmpty()) throw new IllegalArgumentException("Ya no existe la publicación " + id);
        JsonNode published = outputs.path("published");
        if (!published.isBoolean()) throw new IllegalArgumentException("Falta «outputs.published» (true o false)");
        String url = outputs.path("url").asString("").strip();
        String external = outputs.path("external_id").asString("").strip();
        if (published.asBoolean() && url.isEmpty() && external.isEmpty()) {
            throw new IllegalArgumentException("Para darla por publicada hace falta el enlace o el identificador");
        }
        if (!url.isEmpty() && !url.startsWith("https://")) throw new IllegalArgumentException("El enlace no es https: " + url);
        return engine.checked(id, job.taskId(), published.asBoolean(), external.isEmpty() ? null : external,
                url.isEmpty() ? null : url, outputs.path("note").asString("").strip());
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        engine.routeFailed(job.inputs().getFirst(), job.taskId(), reason);
    }
}
