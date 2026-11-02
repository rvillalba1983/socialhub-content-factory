package com.socialhub.demo;

import java.util.List;

/** Estructura de {@code demo/demo-data.json}. Las fechas son desplazamientos en días respecto a hoy. */
record DemoData(List<String> commonRules, List<BrandSeed> brands, List<PromotionSeed> promotions,
                List<StationSeed> stations, List<TaskSeed> tasks, List<ArmorySeed> armory) {

    record BrandSeed(String id, String name, String handle, String color) {}

    record MilestoneSeed(int start, Integer end, String label) {}

    record ResourceSeed(String kind, String location, String description) {}

    record AutomationSeed(String name, String schedule, String model, String runsOn, String status) {}

    record RouteSeed(String name, String status, String label, String notes) {}

    record SettingSeed(String label, String value, String origin) {}

    record PromotionSeed(String id, String brandId, String name, String description, String scope, String cadence,
                         int startOffset, int endOffset, String metricsSource, List<String> goals, List<String> rules,
                         List<String> humanTodos, List<MilestoneSeed> milestones, List<ResourceSeed> resources,
                         List<AutomationSeed> automations, List<RouteSeed> routes, List<SettingSeed> settings) {}

    record StationSeed(String id, String name, String status, String description, List<String> tools, String limit,
                       String note) {}

    record TaskSeed(String id, String station, String promotion, String type, String title, String status,
                    Double progress, List<String> agent, String why, String reason, String error, String result,
                    String goal, List<String> inputs, List<List<Object>> steps, List<List<Object>> checks,
                    Boolean human, Integer attempts, Integer maxAttempts, List<String> deps, Integer startedMinutesAgo,
                    List<List<Object>> events) {}

    record ArmorySeed(String id, String kind, String name, String description, String status, String note,
                      List<String> capabilities) {}
}
