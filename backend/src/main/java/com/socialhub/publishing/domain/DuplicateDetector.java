package com.socialhub.publishing.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Posibles duplicados entre publicaciones activas (no canceladas) de la misma marca y red: el mismo día con el mismo título,
 * o con exactamente la misma hora y el mismo texto. Solo avisa: no bloquea nada. Compara sin mayúsculas, sin tildes y sin
 * diferencias de espacios.
 */
public final class DuplicateDetector {

    public enum Reason { SAME_TITLE, SAME_TIME_AND_TEXT }

    /** Dos o más publicaciones que se repiten entre sí, por orden de hora. */
    public record Group(String brandId, Platform platform, Reason reason, List<String> publicationIds, String promotionId,
                        String title, LocalDate day) {}

    private DuplicateDetector() {}

    /** Texto comparable: sin tildes, en minúsculas y con los espacios (y saltos de línea) colapsados. */
    public static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /**
     * @param brandOf marca de cada promoción (una promoción sin marca conocida se ignora)
     * @return los grupos, ordenados por la hora de su primera publicación
     */
    public static List<Group> find(Collection<Publication> publications, Function<String, String> brandOf) {
        record TitleKey(String brand, Platform platform, LocalDate day, String title) {}
        record TextKey(String brand, Platform platform, Instant at, String text) {}
        Map<TitleKey, List<Publication>> byTitle = new LinkedHashMap<>();
        Map<TextKey, List<Publication>> byText = new LinkedHashMap<>();
        for (Publication p : publications) {
            if (p.getStatus() == PublicationStatus.CANCELLED) continue;
            String brand = brandOf.apply(p.getPromotionId());
            if (brand == null) continue;
            String title = normalize(p.getTitle());
            if (!title.isEmpty()) {
                byTitle.computeIfAbsent(new TitleKey(brand, p.getPlatform(), p.getScheduledOn(), title), k -> new ArrayList<>()).add(p);
            }
            String text = p.getCaption() == null ? "" : normalize(p.getCaption());
            if (!text.isEmpty()) {
                byText.computeIfAbsent(new TextKey(brand, p.getPlatform(), p.scheduledAt(), text), k -> new ArrayList<>()).add(p);
            }
        }
        List<Group> groups = new ArrayList<>();
        byTitle.forEach((k, list) -> {
            if (list.size() > 1) groups.add(group(k.brand(), k.platform(), Reason.SAME_TITLE, list));
        });
        byText.forEach((k, list) -> {
            if (list.size() > 1) groups.add(group(k.brand(), k.platform(), Reason.SAME_TIME_AND_TEXT, list));
        });
        groups.sort(java.util.Comparator.comparing(g -> g.publicationIds().getFirst()));
        return List.copyOf(groups);
    }

    private static Group group(String brand, Platform platform, Reason reason, List<Publication> list) {
        List<String> ids = list.stream().sorted(java.util.Comparator.comparing(Publication::scheduledAt)
                .thenComparing(Publication::getId)).map(Publication::getId).toList();
        var first = list.stream().min(java.util.Comparator.comparing(Publication::scheduledAt)
                .thenComparing(Publication::getId)).orElseThrow();
        return new Group(brand, platform, reason, ids, first.getPromotionId(), first.getTitle(), first.getScheduledOn());
    }
}
