package com.socialhub.demo.domain;

import java.util.List;

/** Estructura de {@code demo/demo-data.json}. Las fechas son desplazamientos en días respecto a hoy. */
public record DemoData(List<String> commonRules, List<BrandSeed> brands, List<PromotionSeed> promotions,
                List<StationSeed> stations, List<TaskSeed> tasks, List<ArmorySeed> armory) {

    public record BrandSeed(String id, String name, String handle, String color) {}

    public record MilestoneSeed(int start, Integer end, String label) {}

    public record ResourceSeed(String kind, String location, String description) {}

    public record AutomationSeed(String name, String schedule, String model, String runsOn, String status) {}

    public record RouteSeed(String name, String status, String label, String notes) {}

    public record SettingSeed(String label, String value, String origin) {}

    public record PromotionSeed(String id, String brandId, String name, String description, String scope, String cadence,
                         int startOffset, int endOffset, String metricsSource, List<String> goals, List<String> rules,
                         List<String> humanTodos, List<MilestoneSeed> milestones, List<ResourceSeed> resources,
                         List<AutomationSeed> automations, List<RouteSeed> routes, List<SettingSeed> settings) {}

    public record StationSeed(String id, String name, String status, String description, List<String> tools, String limit,
                       String note) {}

    public record TaskSeed(String id, String station, String promotion, String type, String title, String status,
                    Double progress, List<String> agent, String why, String reason, String error, String result,
                    String goal, List<String> inputs, List<List<Object>> steps, List<List<Object>> checks,
                    Boolean human, Integer attempts, Integer maxAttempts, List<String> deps, Integer startedMinutesAgo,
                    List<List<Object>> events) {}

    public record ArmorySeed(String id, String kind, String name, String description, String status, String note,
                      List<String> capabilities) {}
}
