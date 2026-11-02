package com.socialhub.promotion.domain;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Datos de una promoción al darla de alta o editarla. El identificador lo asigna SocialHub. */
public record PromotionDraft(String brandId, String name, @Nullable String description, @Nullable String scope,
                             @Nullable String cadence, LocalDate startsOn, LocalDate endsOn, String status,
                             @Nullable String metricsSource, @Nullable List<String> goals, @Nullable List<String> rules,
                             @Nullable List<String> humanTodos, @Nullable List<Promotion.Milestone> milestones,
                             @Nullable List<Promotion.Resource> resources, @Nullable List<Promotion.Automation> automations,
                             @Nullable List<Promotion.Route> routes, @Nullable List<Promotion.Setting> settings) {

    /** La promoción con el identificador y la marca de ejemplo dados; listas vacías en lugar de nulas. */
    public Promotion toPromotion(String id, boolean demo) {
        return new Promotion(id, brandId, name, nz(description), nz(scope), nz(cadence), startsOn, endsOn, status,
                nz(metricsSource), nz(goals), nz(rules), nz(humanTodos), nz(milestones), nz(resources), nz(automations),
                nz(routes), nz(settings), demo);
    }

    private static String nz(@Nullable String s) { return s == null ? "" : s.strip(); }

    private static <T> List<T> nz(@Nullable List<T> list) { return list == null ? List.of() : List.copyOf(list); }
}
