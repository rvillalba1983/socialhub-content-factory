package com.socialhub.factory;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.api.model.RecommendationTargetDto;
import com.socialhub.backup.BackupService;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * «Qué hacer ahora»: de 0 a {@value #MAX} recomendaciones, calculadas en cada consulta y sin guardarlas, así que
 * desaparecen solas cuando dejan de hacer falta. Por orden de prioridad:
 * <ol>
 *   <li>Propuestas de una promoción retenidas, con el motivo.</li>
 *   <li>Propuestas listas para revisar, con su contador.</li>
 *   <li>Marca sin todas sus carpetas.</li>
 *   <li>Encargo enviado antes de una recogida ya pasada y sin recoger (con 30 minutos de margen).</li>
 *   <li>Marca sin cuenta de Publora, salvo que Raúl haya marcado «No uso Publora» para ella.</li>
 *   <li>Sin carpeta de copias.</li>
 *   <li>Marca sin ninguna promoción activa.</li>
 *   <li>Propuestas pedidas y en camino, con la hora de la próxima recogida.</li>
 * </ol>
 * Solo tiene sentido en modo real; quien lo llama decide.
 */
@Component
@RequiredArgsConstructor
class FactoryRecommendations {

    static final int MAX = 3;

    private final AiHeldOrders heldOrders;
    private final AiSentOrders sentOrders;
    private final PromotionQueries promotions;
    private final PublishingQueries publishing;
    private final PubloraService publora;
    private final BackupService backups;
    private final Clock clock;

    List<RecommendationDto> compute() {
        Instant now = clock.instant();
        var brands = promotions.brandRefs();
        var allPromotions = promotions.promotionRefs();
        Map<String, PromotionQueries.PromotionRef> promotionById = allPromotions.stream()
                .collect(Collectors.toMap(PromotionQueries.PromotionRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<AiSentOrders.Sent> sent = sentOrders.waiting();
        List<RecommendationDto> all = new ArrayList<>();

        // 1. Propuestas retenidas
        Map<String, AiHeldOrders.Held> held = new LinkedHashMap<>();
        for (var h : heldOrders.current()) {
            if (h.isPromotionProposals() && promotionById.containsKey(h.promotionId())) held.putIfAbsent(h.promotionId(), h);
        }
        held.forEach((promotionId, h) -> {
            var promotion = promotionById.get(promotionId);
            all.add(recommendation("held-proposals:" + promotionId, RecommendationDto.KindEnum.HELD_PROPOSALS,
                    "Las propuestas de «" + promotion.name() + "» no pueden salir", h.reason(),
                    RecommendationTargetDto.ScreenEnum.BRAND_FOLDERS, promotion.brandId(), null).promotionId(promotionId));
        });

        // 2. Propuestas para revisar
        Map<String, Long> toReview = publishing.proposalCounts();
        for (var promotion : allPromotions) {
            long n = toReview.getOrDefault(promotion.id(), 0L);
            if (n == 0) continue;
            all.add(recommendation("proposals-review:" + promotion.id(), RecommendationDto.KindEnum.PROPOSALS_TO_REVIEW,
                    "Tienes " + n + (n == 1 ? " propuesta" : " propuestas") + " de «" + promotion.name() + "» para revisar",
                    null, RecommendationTargetDto.ScreenEnum.PROPOSALS, promotion.brandId(), promotion.id()).promotionId(promotion.id()));
        }

        // 3. Marca sin todas sus carpetas
        for (var brand : brands) {
            if (promotions.hasAllBrandFolders(brand.id())) continue;
            all.add(recommendation("brand-folders:" + brand.id(), RecommendationDto.KindEnum.BRAND_FOLDERS_MISSING,
                    "Crea las carpetas de " + brand.name(), null,
                    RecommendationTargetDto.ScreenEnum.BRAND_FOLDERS, brand.id(), null));
        }

        // 4. Encargo sin recoger (los del PC los recoge otra tarea y no se miran)
        sent.stream().filter(s -> !s.onPc() && ClaudePickups.overdue(s.sentAt(), now))
                .map(s -> ClaudePickups.nextAfter(s.sentAt()))
                .max(Comparator.naturalOrder())
                .ifPresent(pickup -> all.add(recommendation("orders-not-collected",
                        RecommendationDto.KindEnum.ORDERS_NOT_COLLECTED,
                        "Claude no ha recogido los encargos de las " + hour(pickup) + ". Revisa la tarea programada",
                        null, null, null, null)));

        // 5. Marca sin cuenta de Publora
        for (var brand : brands) {
            if (publora.hasAccounts(brand.id()) || publora.isDismissed(brand.id())) continue;
            all.add(recommendation("publora:" + brand.id(), RecommendationDto.KindEnum.PUBLORA_MISSING,
                    "Conecta Publora para " + brand.name(), null,
                    RecommendationTargetDto.ScreenEnum.PUBLORA, brand.id(), null));
        }

        // 6. Sin carpeta de copias
        if (backups.folders().chosen() == null) {
            all.add(recommendation("backup-folder", RecommendationDto.KindEnum.BACKUP_FOLDER_MISSING,
                    "Elige dónde guardar las copias", null, RecommendationTargetDto.ScreenEnum.BACKUPS, null, null));
        }

        // 7. Marca sin promoción activa
        for (var brand : brands) {
            if (allPromotions.stream().anyMatch(p -> p.active() && p.brandId().equals(brand.id()))) continue;
            all.add(recommendation("no-promotion:" + brand.id(), RecommendationDto.KindEnum.NO_ACTIVE_PROMOTION,
                    "Da de alta la primera promoción de " + brand.name(), null,
                    RecommendationTargetDto.ScreenEnum.NEW_PROMOTION, brand.id(), null));
        }

        // 8. Propuestas pedidas y en camino
        Map<String, Instant> waiting = new LinkedHashMap<>();
        for (var s : sent) {
            if (s.type() == AiRequestType.WEEKLY_PROPOSALS && s.promotionId() != null
                    && promotionById.containsKey(s.promotionId())) {
                waiting.putIfAbsent(s.promotionId(), s.sentAt());
            }
        }
        ZonedDateTime next = ClaudePickups.nextAfter(now);
        boolean tomorrow = !next.toLocalDate().equals(now.atZone(ClaudePickups.ZONE).toLocalDate());
        String eta = (tomorrow ? "mañana " : "") + "sobre las " + hour(next);
        waiting.keySet().forEach(promotionId -> all.add(recommendation("proposals-pending:" + promotionId,
                RecommendationDto.KindEnum.PROPOSALS_PENDING,
                "Propuestas de «" + promotionById.get(promotionId).name() + "» pedidas. Llegarán " + eta,
                null, null, null, null).promotionId(promotionId).etaText(eta)));

        return List.copyOf(all.subList(0, Math.min(MAX, all.size())));
    }

    private static String hour(ZonedDateTime time) {
        return time.getHour() + ":" + String.format("%02d", time.getMinute());
    }

    private static RecommendationDto recommendation(String id, RecommendationDto.KindEnum kind, String title,
                                                    @Nullable String detail,
                                                    RecommendationTargetDto.@Nullable ScreenEnum screen,
                                                    @Nullable String brandId, @Nullable String promotionId) {
        var dto = new RecommendationDto(id, kind, title);
        if (detail != null && !detail.isBlank()) dto.setDetail(detail);
        if (screen != null) {
            var target = new RecommendationTargetDto(screen);
            target.setBrandId(brandId);
            target.setPromotionId(promotionId);
            dto.setTarget(target);
        }
        return dto;
    }
}
