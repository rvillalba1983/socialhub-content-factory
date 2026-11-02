package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Elementos de las colecciones de una promoción (una tabla por tipo, ordenadas por {@code position}). */
final class PromotionParts {

    private PromotionParts() {}

    @Embeddable @Getter @NoArgsConstructor
    static class MilestoneRow {
        private LocalDate startsOn;
        private @Nullable LocalDate endsOn;
        private String label;
    }

    @Embeddable @Getter @NoArgsConstructor
    static class ResourceRow {
        private String kind;
        private String location;
        private String description;
    }

    @Embeddable @Getter @NoArgsConstructor
    static class AutomationRow {
        private String name;
        private String schedule;
        private String model;
        private String runsOn;
        private String status;
    }

    @Embeddable @Getter @NoArgsConstructor
    static class RouteRow {
        private String name;
        private String status;
        private String label;
        private String notes;
    }

    @Embeddable @Getter @NoArgsConstructor
    static class SettingRow {
        private String label;
        private String value;
        private String origin;
    }
}
