package com.socialhub.promotion.domain;

import java.time.LocalDate;
import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Una promoción de una marca, con su calendario de hitos, recursos, automatizaciones, rutas de publicación
 * y configuración. Los campos de tipo texto con valores fijos (estado, modelo…) los valida la base de datos.
 */
@AggregateRoot
public record Promotion(@Identity String id, String brandId, String name, String description, String scope,
                        String cadence, LocalDate startsOn, LocalDate endsOn, String status, String metricsSource,
                        List<String> goals, List<String> rules, List<String> humanTodos, List<Milestone> milestones,
                        List<Resource> resources, List<Automation> automations, List<Route> routes,
                        List<Setting> settings, boolean demo) {

    @ValueObject
    public record Milestone(LocalDate startsOn, @Nullable LocalDate endsOn, String label) {}

    @ValueObject
    public record Resource(String kind, String location, String description) {}

    @ValueObject
    public record Automation(String name, String schedule, String model, String runsOn, String status) {}

    @ValueObject
    public record Route(String name, String status, String label, String notes) {}

    @ValueObject
    public record Setting(String label, String value, String origin) {}
}
