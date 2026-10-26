package com.socialhub.brand;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marcas y promociones, con todo lo que muestra su centro de control. */
@Service
@Transactional(readOnly = true)
public class PromotionQueries {

    private final JdbcClient jdbc;

    public PromotionQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record BrandView(String id, String name, String handle, String color, boolean demo) {}

    public record Milestone(LocalDate startsOn, LocalDate endsOn, String label) {}

    public record Resource(String kind, String location, String description) {}

    public record Automation(String name, String schedule, String model, String runsOn, String status) {}

    public record Route(String name, String status, String label, String notes) {}

    public record Setting(String label, String value, String origin) {}

    public record PromotionView(String id, String brandId, String name, String description, String scope, String cadence,
                                LocalDate startsOn, LocalDate endsOn, String status, String metricsSource,
                                List<String> goals, List<String> rules, List<String> humanTodos,
                                List<Milestone> milestones, List<Resource> resources, List<Automation> automations,
                                List<Route> routes, List<Setting> settings, boolean demo) {}

    public List<BrandView> brands() {
        return jdbc.sql("select * from brand order by position, id")
                .query((rs, n) -> new BrandView(rs.getString("id"), rs.getString("name"), rs.getString("handle"),
                        rs.getString("color"), rs.getBoolean("is_demo")))
                .list();
    }

    public List<PromotionView> promotions() {
        return jdbc.sql("select id from promotion order by position, id").query(String.class).list().stream()
                .map(id -> promotion(id).orElseThrow()).toList();
    }

    public Optional<PromotionView> promotion(String id) {
        return jdbc.sql("select * from promotion where id = ?").param(id).query((rs, n) -> new PromotionView(
                rs.getString("id"), rs.getString("brand_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("scope"), rs.getString("cadence"), rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class), rs.getString("status"), rs.getString("metrics_source"),
                texts("promotion_goal", id), texts("promotion_rule", id), texts("promotion_human_todo", id),
                jdbc.sql("select * from promotion_milestone where promotion_id = ? order by position").param(id)
                        .query((r, i) -> new Milestone(r.getObject("starts_on", LocalDate.class),
                                r.getObject("ends_on", LocalDate.class), r.getString("label"))).list(),
                jdbc.sql("select * from promotion_resource where promotion_id = ? order by position").param(id)
                        .query((r, i) -> new Resource(r.getString("kind"), r.getString("location"),
                                r.getString("description"))).list(),
                jdbc.sql("select * from promotion_automation where promotion_id = ? order by position").param(id)
                        .query((r, i) -> new Automation(r.getString("name"), r.getString("schedule"), r.getString("model"),
                                r.getString("runs_on"), r.getString("status"))).list(),
                jdbc.sql("select * from promotion_route where promotion_id = ? order by position").param(id)
                        .query((r, i) -> new Route(r.getString("name"), r.getString("status"), r.getString("label"),
                                r.getString("notes"))).list(),
                jdbc.sql("select * from promotion_setting where promotion_id = ? order by position").param(id)
                        .query((r, i) -> new Setting(r.getString("label"), r.getString("value"), r.getString("origin")))
                        .list(),
                rs.getBoolean("is_demo"))).optional();
    }

    public List<String> commonRules() {
        return jdbc.sql("select text from app_rule order by position").query(String.class).list();
    }

    private List<String> texts(String table, String promotionId) {
        return jdbc.sql("select text from " + table + " where promotion_id = ? order by position")
                .param(promotionId).query(String.class).list();
    }
}
