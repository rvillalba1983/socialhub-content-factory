package com.socialhub.factory.application;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.armory.ArmoryQueries;
import com.socialhub.factory.FactoryAttention;
import com.socialhub.factory.domain.Attention;
import com.socialhub.factory.domain.FactoryAlert;
import com.socialhub.factory.domain.Recommendation;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionAlert;
import com.socialhub.production.ProductionQueries;
import com.socialhub.publishing.PublerAccounts;
import com.socialhub.publishing.PublishingQueries;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A los avisos de producción se suman los encargos retenidos, lo que impide publicar, las cuentas de Publer llenas, los de
 * publicación (a mano, con error o con posible duplicado) y los servicios reales no disponibles (por ejemplo, Drive sin
 * montar).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FactoryAttentionService implements FactoryAttention {

    private final ProductionQueries production;
    private final PublishingQueries publishing;
    private final ArmoryQueries armory;
    private final AiHeldOrders heldOrders;
    private final PublerAccounts publer;
    private final FactoryRecommendations recommendations;
    private final AppModeHolder mode;

    @Override
    public Attention attention() {
        boolean real = mode.selected() == AppMode.REAL;
        List<Recommendation> recs = real ? recommendations.compute() : List.of();
        return new Attention(alerts(production.alerts(), armory.blockedServices(), publishing.publishAlerts(),
                real ? heldOrders.current() : List.of(), recs, real ? publer.full() : List.of()), recs);
    }

    private static List<FactoryAlert> alerts(List<ProductionAlert> production, List<ArmoryQueries.BlockedService> blocked,
                                             List<PublishingQueries.PublishAlert> publishing, List<AiHeldOrders.Held> held,
                                             List<Recommendation> recommendations, List<PublerAccounts.Full> fullPubler) {
        List<FactoryAlert> alerts = new ArrayList<>();
        for (var p : production) {
            alerts.add(new FactoryAlert(FactoryAlert.Kind.valueOf(p.kind().name()), FactoryAlert.TargetType.valueOf(p.target().name()),
                    p.targetId(), p.stationId(), p.title(), p.detail()));
        }
        // Encargos a Claude que no pueden salir hasta que Raúl arregle algo (por ejemplo, una carpeta)
        for (var h : held) {
            // Las propuestas retenidas de una promoción salen solo en «Qué hacer ahora», que ya no recorta la lista
            if (h.isPromotionProposals() && h.promotionId() != null) continue;
            alerts.add(new FactoryAlert(FactoryAlert.Kind.BLOCKED, FactoryAlert.TargetType.TASK, h.taskId(), h.stationId(),
                    "En cola: " + h.title(), h.reason()));
        }
        // Lo que impide publicar sale también en «Necesita atención», sacado de las mismas recomendaciones (un solo cálculo)
        for (var r : recommendations) {
            boolean silent = r.kind() == Recommendation.Kind.PC_TASK_SILENT;
            if (!silent && r.kind() != Recommendation.Kind.NO_PUBLISHING_ROUTE) continue;
            alerts.add(new FactoryAlert(silent ? FactoryAlert.Kind.FAILED : FactoryAlert.Kind.BLOCKED,
                    FactoryAlert.TargetType.ARMORY, silent ? ServicesWatch.PC_ID : ServicesWatch.PUBLORA_ID, "prog",
                    r.title(), r.detail() == null ? "" : r.detail()));
        }
        // Cuenta de Publer llena (plan gratuito): publicaciones en espera, con el botón de pasarlas a Publora si se puede
        for (var f : fullPubler) {
            String detail = f.movable() > 0
                    ? f.movable() + (f.movable() == 1 ? " puede" : " pueden") + " pasar ya a Publora"
                            + (f.withoutPublora().isEmpty() ? ""
                                    : "; Publora no está configurada para " + String.join(", ", f.withoutPublora()))
                    : "Publora no está configurada para " + String.join(", ", f.withoutPublora());
            alerts.add(new FactoryAlert(FactoryAlert.Kind.BLOCKED, FactoryAlert.TargetType.STATION, "pub", "pub",
                    "La cuenta «" + f.accountName() + "» de Publer está llena: " + f.waiting()
                            + (f.waiting() == 1 ? " publicación en espera" : " publicaciones en espera"),
                    detail, f.movable() > 0 ? new FactoryAlert.Action("Pasar a Publora", f.accountName(), f.platform()) : null));
        }
        for (var a : publishing) {
            alerts.add(new FactoryAlert(a.failed() ? FactoryAlert.Kind.FAILED : FactoryAlert.Kind.BLOCKED,
                    FactoryAlert.TargetType.PUBLICATION, a.publicationId(), "pub", a.title(), a.detail()));
        }
        for (var item : blocked) {
            alerts.add(new FactoryAlert(FactoryAlert.Kind.BLOCKED, FactoryAlert.TargetType.ARMORY, item.id(), "gen",
                    item.name(), item.note()));
        }
        return alerts;
    }
}
