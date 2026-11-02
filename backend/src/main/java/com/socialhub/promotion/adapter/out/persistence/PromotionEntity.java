package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabla {@code promotion}. Las colecciones se cargan por lotes ({@code default_batch_fetch_size}). */
@Entity
@Table(name = "promotion")
@Getter
@NoArgsConstructor
class PromotionEntity {

    @Id private String id;
    private String brandId;
    private String name;
    private String description;
    private String scope;
    private String cadence;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private String status;
    private String metricsSource;
    private int position;
    @Column(name = "is_demo") private boolean demo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> goals = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> rules = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> humanTodos = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "promotion_milestone", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "position")
    private List<PromotionParts.MilestoneRow> milestones = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "promotion_resource", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "position")
    private List<PromotionParts.ResourceRow> resources = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "promotion_automation", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "position")
    private List<PromotionParts.AutomationRow> automations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "promotion_route", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "position")
    private List<PromotionParts.RouteRow> routes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "promotion_setting", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "position")
    private List<PromotionParts.SettingRow> settings = new ArrayList<>();
}
