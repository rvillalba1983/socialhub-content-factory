package com.socialhub.controlcenter;

import com.socialhub.armory.ArmoryQueries;
import com.socialhub.audit.AuditLog;
import com.socialhub.brand.PromotionQueries;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.production.ProductionQueries;
import com.socialhub.publishing.PublicationQueries;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lecturas que alimentan el anillo y los centros de control. El estado sale siempre de la base de datos. */
@RestController
@RequestMapping("/api/v1")
class ControlCenterController {

    private final ProductionQueries production;
    private final PromotionQueries promotions;
    private final PublicationQueries publications;
    private final ArmoryQueries armory;
    private final AuditLog audit;
    private final SocialHubProperties properties;
    private final Clock clock;

    ControlCenterController(ProductionQueries production, PromotionQueries promotions, PublicationQueries publications,
                            ArmoryQueries armory, AuditLog audit, SocialHubProperties properties, Clock clock) {
        this.production = production;
        this.promotions = promotions;
        this.publications = publications;
        this.armory = armory;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param kind err (error en una tarea) o block (estación bloqueada)
     * @param target task o station
     */
    record Alert(String kind, String target, String id, String stationId, String title, String detail) {}

    record FactorySnapshot(ZonedDateTime now, boolean demo, List<PromotionQueries.BrandView> brands,
                           List<PromotionQueries.PromotionView> promotions, List<ProductionQueries.StationView> stations,
                           List<ProductionQueries.TaskView> tasks, List<PublicationQueries.PublicationView> publications,
                           List<ArmoryQueries.ArmoryItem> armory, List<String> commonRules, List<Alert> alerts) {}

    record PromotionDetail(PromotionQueries.PromotionView promotion, List<PublicationQueries.PublicationView> publications,
                           List<ProductionQueries.TaskView> tasks) {}

    @GetMapping("/factory")
    @Transactional(readOnly = true)
    FactorySnapshot factory() {
        List<ProductionQueries.StationView> stations = production.stations();
        List<ProductionQueries.TaskView> tasks = production.tasks();
        return new FactorySnapshot(ZonedDateTime.now(clock), properties.demo().enabled(), promotions.brands(),
                promotions.promotions(), stations, tasks, publications.all(), armory.items(), promotions.commonRules(),
                alerts(stations, tasks));
    }

    @GetMapping("/promotions/{id}")
    @Transactional(readOnly = true)
    PromotionDetail promotion(@PathVariable String id) {
        var promotion = promotions.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
        var tasks = production.tasks().stream()
                .filter(t -> t.promotionId() == null || t.promotionId().equals(id)).toList();
        return new PromotionDetail(promotion, publications.byPromotion(id), tasks);
    }

    @GetMapping("/audit")
    List<AuditLog.Entry> audit(@RequestParam(defaultValue = "50") int limit) {
        return audit.latest(Math.min(Math.max(limit, 1), 500));
    }

    private static List<Alert> alerts(List<ProductionQueries.StationView> stations, List<ProductionQueries.TaskView> tasks) {
        Map<String, String> names = stations.stream()
                .collect(Collectors.toMap(ProductionQueries.StationView::id, ProductionQueries.StationView::name));
        List<Alert> alerts = new ArrayList<>();
        tasks.stream().filter(t -> "err".equals(t.status())).forEach(t -> alerts.add(new Alert("err", "task", t.id(),
                t.stationId(), names.get(t.stationId()), "Error: " + t.title())));
        stations.stream().filter(s -> "block".equals(s.status())).forEach(s -> alerts.add(new Alert("block", "station",
                s.id(), s.id(), s.name(), s.note() == null ? "Bloqueada" : s.note())));
        return alerts;
    }
}
