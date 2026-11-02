package com.socialhub.publishing;

import com.socialhub.ai.AiContextProvider;
import com.socialhub.promotion.EditorialDecision;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.EditorialMemoryRepository;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoria editorial de una marca: lo publicado (8 semanas), los últimos rechazos y correcciones (20) y todas sus
 * decisiones editoriales. Va en cada encargo a Claude para que no empiece de cero ni repita.
 */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class EditorialMemory implements AiContextProvider {

    static final int WEEKS = 8;
    static final int LIMIT = 20;
    private static final Set<PublicationStatus> DONE = EnumSet.of(PublicationStatus.APPROVED, PublicationStatus.SCHEDULED,
            PublicationStatus.PUBLISHING, PublicationStatus.PUBLISHED);

    private final PromotionQueries promotions;
    private final PublicationRepository publications;
    private final EditorialMemoryRepository memory;
    private final Clock clock;

    public record Snapshot(List<EditorialDecision> decisions, List<EditorialMemoryRepository.Rejection> rejections,
                           List<EditorialMemoryRepository.Correction> corrections, List<Publication> published) {}

    @Transactional(readOnly = true)
    public Snapshot snapshot(String brandId) {
        Set<String> ids = new HashSet<>(promotions.promotionIdsOf(brandId));
        LocalDate since = LocalDate.now(clock).minusWeeks(WEEKS);
        List<Publication> published = publications.findAll().stream()
                .filter(p -> ids.contains(p.getPromotionId()) && DONE.contains(p.getStatus()) && !p.getScheduledOn().isBefore(since))
                .sorted(Comparator.comparing(Publication::getScheduledOn).thenComparing(Publication::getScheduledTime).reversed())
                .toList();
        return new Snapshot(promotions.decisions(brandId), memory.rejections(brandId, LIMIT), memory.corrections(brandId, LIMIT),
                published);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> context(String promotionId, String brandId) {
        Snapshot s = snapshot(brandId);
        if (s.decisions().isEmpty() && s.rejections().isEmpty() && s.corrections().isEmpty() && s.published().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decisions", s.decisions().stream().map(EditorialDecision::text).toList());
        m.put("published_last_8_weeks", s.published().stream().map(p -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("date", p.getScheduledOn().toString());
            x.put("platform", p.getPlatform().name());
            x.put("type", p.getType());
            x.put("title", p.getTitle());
            x.put("status", p.getStatus().name());
            if (p.getCaption() != null) x.put("caption", shorten(p.getCaption(), 300));
            return x;
        }).toList());
        m.put("rejections", s.rejections().stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("what", r.kind() == EditorialMemoryRepository.Kind.TEXT ? "texto descartado" : "publicación cancelada");
            x.put("title", r.title());
            x.put("reason", r.reason().label());
            if (r.note() != null) x.put("note", r.note());
            if (r.content() != null) x.put("content", shorten(r.content(), 300));
            return x;
        }).toList());
        m.put("corrections", s.corrections().stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            put(x, "claude_caption", c.beforeCaption());
            put(x, "raul_caption", c.afterCaption());
            put(x, "claude_hashtags", c.beforeHashtags());
            put(x, "raul_hashtags", c.afterHashtags());
            return x;
        }).toList());
        return Map.of("editorial_memory", m);
    }

    private static void put(Map<String, Object> m, String key, @Nullable String value) {
        if (value != null) m.put(key, shorten(value, 600));
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
