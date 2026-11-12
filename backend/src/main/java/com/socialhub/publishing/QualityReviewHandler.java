package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.QualityIssue;
import com.socialhub.publishing.domain.QualityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Revisión de calidad con Claude: coherencia del mensaje, tono de la marca, ortografía y afirmaciones sin fuente de
 * una publicación. Claude devuelve {@code outputs.issues: [{message}]} (vacía si está bien) y sus avisos sustituyen a
 * los de su revisión anterior. Se aceptan igual que los del código.
 */
@Component
@RequiredArgsConstructor
class QualityReviewHandler implements AiTypeHandler {

    static final int MAX_ISSUES = 20;

    private final PublishingQueries queries;
    private final PublicationFileRepository files;
    private final QualityService quality;

    @Override public AiRequestType type() { return AiRequestType.QUALITY_REVIEW; }

    @Override
    public java.time.@org.jspecify.annotations.Nullable Instant contentDueAt(AiJob job) {
        return queries.earliestScheduled(job.inputs()).orElse(null);
    }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.size() != 1) throw new IllegalArgumentException("Indica una publicación (su identificador)");
        queries.find(inputs.getFirst()).filter(p -> p.getPromotionId().equals(promotionId))
                .orElseThrow(() -> new IllegalArgumentException("La publicación «" + inputs.getFirst() + "» no existe en esta promoción"));
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        String id = job.inputs().getFirst();
        Map<String, Object> inputs = new LinkedHashMap<>();
        queries.find(id).ifPresent(p -> inputs.put("publication", PublishingQueries.forAi(p)));
        inputs.put("files", files.files(id).stream().map(PublicationFile::path).toList());
        inputs.put("automatic_checks", quality.issues(id).stream()
                .filter(i -> i.source() == QualityIssue.Source.CODE).map(QualityIssue::message).toList());
        inputs.put("expected_outputs", "issues: [{message}] con lo que hay que corregir (coherencia del mensaje, tono e "
                + "identidad de la marca, ortografía, afirmaciones sin fuente, restricciones de la marca); lista vacía si "
                + "está bien. No repitas lo de automatic_checks. summary: una línea.");
        return inputs;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode items = outputs.path("issues");
        if (!items.isArray()) throw new IllegalArgumentException("Falta la lista de avisos («outputs.issues»)");
        if (items.size() > MAX_ISSUES) throw new IllegalArgumentException("Demasiados avisos (más de " + MAX_ISSUES + ")");
        List<QualityRepository.ReviewIssue> issues = new ArrayList<>();
        for (JsonNode item : items) {
            String message = item.path("message").asString("").strip();
            if (message.isEmpty()) throw new IllegalArgumentException("Hay un aviso sin texto");
            if (message.length() > 1000) message = message.substring(0, 1000);
            String key = "CLAUDE:" + hash(message);
            if (issues.stream().noneMatch(i -> i.key().equals(key))) issues.add(new QualityRepository.ReviewIssue(key, message));
        }
        String publicationId = job.inputs().getFirst();
        if (queries.find(publicationId).isEmpty()) throw new IllegalArgumentException("La publicación ya no existe");
        quality.applyReview(publicationId, issues, job.taskId());
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return (issues.isEmpty() ? "Revisión sin avisos" : issues.size() + (issues.size() == 1 ? " aviso" : " avisos") + " de Claude")
                + (summary.isEmpty() ? "" : " · " + summary);
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
