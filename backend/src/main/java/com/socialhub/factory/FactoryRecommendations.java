package com.socialhub.factory;

import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.AiWeeklySchedule;
import com.socialhub.ai.ClaudeActivity;
import com.socialhub.ai.ClaudePickups;
import com.socialhub.api.model.RecommendationDto;
import com.socialhub.api.model.RecommendationTargetDto;
import com.socialhub.backup.BackupService;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraService;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * «Qué hacer ahora»: la lista completa de recomendaciones, calculadas en cada consulta y sin guardarlas, así que
 * desaparecen solas cuando dejan de hacer falta. El frontal enseña las 3 primeras y el resto con «Ver N más». Por orden de prioridad:
 * <ol>
 *   <li>Marca sin forma de publicar: tiene publicaciones de camino a publicar (en producción, borrador, en revisión o
 *   aprobadas) en los próximos 7 días, la tarea del PC «Publicar con Chrome» no contesta y Publora no se puede usar
 *   para su red (sin clave, sin cuenta de esa red o «No uso Publora»). Va la primera, con la siguiente, porque
 *   bloquea publicaciones reales. Botón a la tarjeta de Publora de la marca si falta algo que Raúl puede conectar; sin
 *   botón si ha elegido no usarla.</li>
 *   <li>La tarea «Publicar con Chrome» no contesta: encargos de {@code para-claude-pc} sin resultado desde hace 90
 *   minutos dentro de su horario (8:40-22:40). Sin botón.</li>
 *   <li>Propuestas de una promoción retenidas, con el motivo (la marca sin carpeta de «Encargos», la carpeta no
 *   disponible porque Drive está cerrado o el límite diario alcanzado, este último con botón a «Claude»). Una sola
 *   fila por promoción, con este orden de motivos.</li>
 *   <li>Propuestas listas para revisar, con su contador.</li>
 *   <li>Marca sin todas sus carpetas.</li>
 *   <li>Encargo sin resultado tras la recogida que le tocaba (5 minutos de sincronización y 30 de margen), si la
 *   carpeta de «Encargos» de su marca está disponible.</li>
 *   <li>Marca sin cuenta de Publora, salvo que Raúl haya marcado «No uso Publora» para ella.</li>
 *   <li>Sin carpeta de copias.</li>
 *   <li>Marca sin ninguna promoción activa ni planificada por empezar (esa se activa sola): dar de alta la primera, o activar una en pausa.</li>
 *   <li>Propuestas pedidas y en camino, con la hora de la próxima recogida.</li>
 *   <li>Especificación semanal en espera: ya tocaría pedirla (domingo desde las 9:00 o durante la semana) pero la
 *   promoción no tiene publicaciones aprobadas esa semana. Informativa, sin botón. Va la última: no hay nada que
 *   hacer salvo aprobar publicaciones, que ya se pide en las filas de propuestas.</li>
 * </ol>
 * Solo tiene sentido en modo real; quien lo llama decide.
 */
@Component
@RequiredArgsConstructor
class FactoryRecommendations {

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
        java.time.LocalDate today = now.atZone(ClaudePickups.ZONE).toLocalDate();
        List<AiSentOrders.Sent> sent = sentOrders.waiting();
        List<RecommendationDto> all = new ArrayList<>();

        // 0. Publicar: la tarea del PC no contesta y, para alguna marca, tampoco hay Publora utilizable
        var pcSilence = ClaudeActivity.pcSilence(sent, now);
        if (pcSilence.isPresent()) {
            var unroutable = publishing.withoutPubloraRoute(today, today.plusDays(7), promotions::brandOf);
            for (var brand : brands) noPublishingRoute(brand, promotionById, unroutable).ifPresent(all::add);
            all.add(pcTaskSilent(pcSilence.get(), now));
        }

        // 1. Propuestas retenidas, o paradas por Drive o por el límite diario
        Map<String, AiHeldOrders.Held> held = new LinkedHashMap<>();
        for (var h : heldOrders.current()) {
            if (h.isPromotionProposals() && promotionById.containsKey(h.promotionId())) held.putIfAbsent(h.promotionId(), h);
        }
        held.forEach((promotionId, h) -> all.add(heldRow(promotionById.get(promotionId), h.reason(),
                RecommendationTargetDto.ScreenEnum.BRAND_FOLDERS)));
        var stalled = heldOrders.stalledProposals().stream()
                .filter(s -> promotionById.containsKey(s.promotionId()))
                .sorted(Comparator.comparing(AiHeldOrders.Stalled::cause)) // Drive antes que el límite
                .toList();
        Set<String> alreadyRow = new java.util.HashSet<>(held.keySet());
        for (var s : stalled) {
            if (!alreadyRow.add(s.promotionId())) continue;
            var promotion = promotionById.get(s.promotionId());
            all.add(s.cause() == AiHeldOrders.Cause.DRIVE_UNAVAILABLE
                    ? heldRow(promotion, "La carpeta de Encargos no está disponible en este ordenador. ¿Está abierto Google Drive?", null)
                    : heldRow(promotion, "Límite diario de encargos a Claude alcanzado (" + s.limit() + "): salen mañana",
                            RecommendationTargetDto.ScreenEnum.CLAUDE));
        }

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
        // Si la carpeta de «Encargos» de la marca no está disponible, no se sabe si Claude ha dejado el resultado
        sent.stream().filter(s -> !s.onPc() && s.ordersAvailable() && ClaudePickups.overdue(s.sentAt(), now))
                .map(s -> ClaudePickups.pickupFor(s.sentAt()))
                .max(Comparator.naturalOrder())
                .ifPresent(pickup -> all.add(recommendation("orders-not-collected",
                        RecommendationDto.KindEnum.ORDERS_NOT_COLLECTED,
                        "No ha llegado el resultado de los encargos de las " + hour(pickup) + ". Revisa la tarea programada",
                        null, null, null, null)));

        // 5. Marca sin cuenta de Publora
        for (var brand : brands) {
            if (publora.hasAccounts(brand.id()) || publora.isDismissed(brand.id())) continue;
            all.add(recommendation("publora:" + brand.id(), RecommendationDto.KindEnum.PUBLORA_MISSING,
                    "Conecta Publora para " + brand.name(), null,
                    RecommendationTargetDto.ScreenEnum.PUBLORA, brand.id(), null));
        }

        // 6. Sin carpeta de copias
        if (!backups.hasChosenFolder()) {
            all.add(recommendation("backup-folder", RecommendationDto.KindEnum.BACKUP_FOLDER_MISSING,
                    "Elige dónde guardar las copias", null, RecommendationTargetDto.ScreenEnum.BACKUPS, null, null));
        }

        // 7. Marca sin promoción activa
        for (var brand : brands) {
            var ofBrand = allPromotions.stream().filter(p -> p.brandId().equals(brand.id())).toList();
            if (ofBrand.stream().anyMatch(PromotionQueries.PromotionRef::active)) continue;
            // Una planificada que aún no ha terminado se activará sola el día que empiece: no hace falta pedirlo
            if (ofBrand.stream().anyMatch(p -> p.plannedAhead(today))) continue;
            // Con promociones planificadas o en pausa se pide activar una (y el botón la abre); si no tiene ninguna,
            // dar de alta la primera. Si solo tiene terminadas, hace falta una nueva.
            var toActivate = ofBrand.stream().filter(p -> "PAUSED".equals(p.status())).findFirst();
            if (toActivate.isPresent()) {
                all.add(recommendation("no-promotion:" + brand.id(), RecommendationDto.KindEnum.NO_ACTIVE_PROMOTION,
                        "Activa una promoción de " + brand.name(), null,
                        RecommendationTargetDto.ScreenEnum.NEW_PROMOTION, brand.id(), toActivate.get().id()));
            } else {
                all.add(recommendation("no-promotion:" + brand.id(), RecommendationDto.KindEnum.NO_ACTIVE_PROMOTION,
                        ofBrand.isEmpty() ? "Da de alta la primera promoción de " + brand.name()
                                : "Da de alta una promoción nueva de " + brand.name(), null,
                        RecommendationTargetDto.ScreenEnum.NEW_PROMOTION, brand.id(), null));
            }
        }

        // 8. Propuestas pedidas y en camino
        Map<String, Instant> waiting = new LinkedHashMap<>();
        for (var s : sent) {
            if (s.type() == AiRequestType.WEEKLY_PROPOSALS && s.promotionId() != null
                    && promotionById.containsKey(s.promotionId())) {
                waiting.putIfAbsent(s.promotionId(), s.sentAt());
            }
        }
        String eta = ClaudePickups.etaText(now);
        waiting.keySet().forEach(promotionId -> all.add(recommendation("proposals-pending:" + promotionId,
                RecommendationDto.KindEnum.PROPOSALS_PENDING,
                "Propuestas de «" + promotionById.get(promotionId).name() + "» pedidas. Llegarán " + eta,
                null, null, null, null).promotionId(promotionId).etaText(eta)));

        // 9. Especificación semanal esperando publicaciones aprobadas
        ZonedDateTime local = now.atZone(ClaudePickups.ZONE);
        AiWeeklySchedule.specWeek(local).ifPresent(week -> {
            String day = week.getDayOfMonth() + " de " + week.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
            for (String promotionId : promotions.activeBetween(week, week.plusDays(6))) {
                var promotion = promotionById.get(promotionId);
                if (promotion == null || !publishing.approvedForAi(promotionId, week, week.plusDays(6)).isEmpty()) continue;
                all.add(recommendation("spec-waiting:" + promotionId, RecommendationDto.KindEnum.SPEC_WAITING,
                        "La especificación de «" + promotion.name() + "» espera a que apruebes publicaciones de la semana del " + day,
                        null, null, null, null).promotionId(promotionId));
            }
        });

        return List.copyOf(all);
    }

    private static RecommendationDto heldRow(PromotionQueries.PromotionRef promotion, String reason,
                                             RecommendationTargetDto.@Nullable ScreenEnum screen) {
        return recommendation("held-proposals:" + promotion.id(), RecommendationDto.KindEnum.HELD_PROPOSALS,
                "Las propuestas de «" + promotion.name() + "» no pueden salir", reason, screen,
                screen == RecommendationTargetDto.ScreenEnum.BRAND_FOLDERS ? promotion.brandId() : null, null)
                .promotionId(promotion.id());
    }

    static final String PC_TASK_SILENT_TITLE = "La tarea «Publicar con Chrome» no contesta";

    private static RecommendationDto pcTaskSilent(ClaudeActivity.Silence silence, Instant now) {
        return recommendation("pc-task-silent", RecommendationDto.KindEnum.PC_TASK_SILENT, PC_TASK_SILENT_TITLE,
                pcDetail(silence, now), null, null, null);
    }

    static String pcDetail(ClaudeActivity.Silence silence, Instant now) {
        return silence.count() + (silence.count() == 1 ? " encargo sin contestar" : " encargos sin contestar") + " desde "
                + ClaudeActivity.since(silence.since(), now) + ". ¿Está activa la tarea «Publicar con Chrome» y abierto Chrome?";
    }

    private java.util.Optional<RecommendationDto> noPublishingRoute(PromotionQueries.BrandRef brand,
            Map<String, PromotionQueries.PromotionRef> promotionById, List<PublishingQueries.Unroutable> unroutable) {
        var ofBrand = unroutable.stream().filter(u -> {
            var promotion = promotionById.get(u.promotionId());
            return promotion != null && promotion.brandId().equals(brand.id());
        }).sorted(Comparator.comparing(PublishingQueries.Unroutable::scheduledOn)).toList();
        if (ofBrand.isEmpty()) return java.util.Optional.empty();
        var first = ofBrand.getFirst();
        boolean dismissed = first.gap() == PubloraService.Gap.DISMISSED;
        String why = switch (first.gap()) {
            case NO_KEY -> "Publora no tiene clave";
            case DISMISSED -> "Publora está marcada como «No uso Publora»";
            default -> "Publora no tiene cuenta de " + first.platform();
        };
        int n = ofBrand.size();
        String detail = n + (n == 1 ? " publicación" : " publicaciones") + " de los próximos 7 días (la primera, el "
                + first.scheduledOn().getDayOfMonth() + " de " + first.scheduledOn().getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"))
                + ") no tiene por dónde salir: la tarea «Publicar con Chrome» no contesta y " + why + ".";
        return java.util.Optional.of(recommendation("no-publishing-route:" + brand.id(), RecommendationDto.KindEnum.NO_PUBLISHING_ROUTE,
                "No hay forma de publicar " + brand.name(), detail,
                dismissed ? null : RecommendationTargetDto.ScreenEnum.PUBLORA, dismissed ? null : brand.id(), null));
    }

    private static String hour(ZonedDateTime time) { return ClaudePickups.hour(time); }

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
