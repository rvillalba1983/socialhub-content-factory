package com.socialhub.ai;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Encargos que salen solos cada semana, para cada promoción activa (hora de España):
 * <ul>
 *   <li>Domingo desde las 9:00: spec.json de la semana que empieza el lunes. Si SocialHub estaba cerrado, o la semana
 *       no tenía aún publicaciones aprobadas, se pide el de la semana en curso en cuanto las haya (hasta el sábado).
 *       Sin publicaciones aprobadas no se pide nada ni se marca como hecho.</li>
 *   <li>Lunes desde las 9:00: balance de la semana pasada. Si estaba cerrado, se pide el martes o el miércoles.</li>
 *   <li>Lunes desde las 18:00: propuestas de la semana siguiente. Si estaba cerrado, se piden el martes o el miércoles.</li>
 * </ul>
 * Además, una promoción nueva pide enseguida las propuestas de su primera semana si ya pasó el lunes en que se habrían
 * pedido (si empieza a mitad de semana, para los días que quedan).
 * Cada encargo se crea una sola vez por promoción y semana. Los demás tipos se piden a mano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiAutoOrders {

    static final LocalTime FROM = LocalTime.of(9, 0);

    private final PromotionQueries promotions;
    private final AiOrders orders;
    private final AiHandlers handlers;
    private final AiAutoOrderRepository done;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(initialDelayString = "${socialhub.ai.auto-initial-delay-millis:60000}",
               fixedDelayString = "${socialhub.ai.auto-millis:600000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                createDue();
            } catch (RuntimeException e) {
                log.warn("Encargos semanales omitidos: {}", e.toString());
            }
        });
    }

    /** @return cuántos encargos se han creado */
    int createDue() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int n = 0;
        Optional<LocalDate> spec = specWeek(now);
        if (spec.isPresent()) n += create(AiRequestType.WEEKLY_SPEC, spec.get());
        Optional<LocalDate> balance = balanceWeek(now);
        if (balance.isPresent()) n += create(AiRequestType.WEEKLY_BALANCE, balance.get());
        Optional<LocalDate> proposals = proposalsWeek(now);
        if (proposals.isPresent()) n += create(AiRequestType.WEEKLY_PROPOSALS, proposals.get());
        n += createFirstWeeks(now);
        return n;
    }

    /**
     * Primera semana de una promoción: sus propuestas se habrían pedido el lunes anterior a las 18:00. Si esa hora ya
     * pasó (la promoción se dio de alta después) y la semana no ha terminado, se piden ya, solo para los días que quedan.
     */
    int createFirstWeeks(ZonedDateTime now) {
        int n = 0;
        for (PromotionQueries.ActiveDates p : promotions.activeDates()) {
            Optional<FirstWeek> first = firstWeek(now, p.startsOn(), p.endsOn());
            if (first.isEmpty()) continue;
            LocalDate week = first.get().week(), from = first.get().from(), to = first.get().to();
            var key = new AiAutoOrderEntity.Key(p.id(), AiRequestType.WEEKLY_PROPOSALS.name(), week);
            if (done.existsById(key)) continue;
            transactions.executeWithoutResult(s -> {
                String title = "Propuestas de la primera semana · del " + from.getDayOfMonth() + " al " + to.getDayOfMonth()
                        + " " + to.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
                String goal = "La promoción empieza ahora: propón sus publicaciones del " + from + " al " + to
                        + ", según su cadencia, sus objetivos, sus hitos y su memoria editorial. No propongas nada antes del "
                        + from + ".";
                String taskId = orders.submit(p.id(), AiRequestType.WEEKLY_PROPOSALS, title, goal, List.of(week.toString()));
                done.save(new AiAutoOrderEntity(p.id(), AiRequestType.WEEKLY_PROPOSALS.name(), week, taskId, clock.instant()));
            });
            log.info("Propuestas de la primera semana pedidas: {} (del {} al {})", p.id(), from, to);
            n++;
        }
        return n;
    }

    /** Primera semana de una promoción: su lunes y los días que quedan por proponer. */
    record FirstWeek(LocalDate week, LocalDate from, LocalDate to) {}

    /**
     * La primera semana toca pedirla ya si pasó el lunes anterior a las 18:00 (cuando se habría pedido sola) y la semana
     * no ha terminado.
     */
    static Optional<FirstWeek> firstWeek(ZonedDateTime now, LocalDate startsOn, LocalDate endsOn) {
        LocalDate today = now.toLocalDate();
        LocalDate week = startsOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ZonedDateTime usual = week.minusWeeks(1).atTime(PROPOSALS_FROM).atZone(now.getZone());
        if (now.isBefore(usual) || !today.isBefore(week.plusWeeks(1)) || endsOn.isBefore(today)) return Optional.empty();
        LocalDate from = startsOn.isAfter(today) ? startsOn : today;
        LocalDate sunday = week.plusDays(6);
        return Optional.of(new FirstWeek(week, from, sunday.isAfter(endsOn) ? endsOn : sunday));
    }

    /** Lunes de la semana cuyo spec toca pedir ahora, si toca alguno. */
    static Optional<LocalDate> specWeek(ZonedDateTime now) { return AiWeeklySchedule.specWeek(now); }

    /** Lunes de la semana cuyo balance toca pedir ahora, si toca alguno. */
    static Optional<LocalDate> balanceWeek(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate lastWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        return switch (today.getDayOfWeek()) {
            case MONDAY -> now.toLocalTime().isBefore(FROM) ? Optional.empty() : Optional.of(lastWeek);
            case TUESDAY, WEDNESDAY -> Optional.of(lastWeek);
            default -> Optional.empty();
        };
    }

    static final LocalTime PROPOSALS_FROM = LocalTime.of(18, 0);

    /** Lunes de la semana cuyas propuestas toca pedir ahora (la siguiente), si toca alguna. */
    static Optional<LocalDate> proposalsWeek(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate next = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        return switch (today.getDayOfWeek()) {
            case MONDAY -> now.toLocalTime().isBefore(PROPOSALS_FROM) ? Optional.empty() : Optional.of(next);
            case TUESDAY, WEDNESDAY -> Optional.of(next);
            default -> Optional.empty();
        };
    }

    private int create(AiRequestType type, LocalDate week) {
        int n = 0;
        List<String> active = promotions.activeBetween(week, week.plusDays(6));
        for (String promotionId : active) {
            var key = new AiAutoOrderEntity.Key(promotionId, type.name(), week);
            if (done.existsById(key)) continue;
            // Sin nada que hacer todavía (p. ej., sin publicaciones aprobadas): no se crea ni se marca como hecho
            if (!handlers.of(type).map(h -> h.readyForAutoOrder(promotionId, week)).orElse(true)) continue;
            transactions.executeWithoutResult(s -> {
                String title = type.label() + " · semana del " + week.getDayOfMonth() + " "
                        + week.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
                String goal = switch (type) {
                    case WEEKLY_SPEC -> "Prepara el spec.json de piezas de la semana del " + week + " para esta promoción: "
                            + "las piezas de sus publicaciones aprobadas, con el material de sus carpetas y sus normas.";
                    case WEEKLY_PROPOSALS -> "Propón las publicaciones de la semana del " + week + " para esta promoción, "
                            + "según su cadencia, sus objetivos, sus hitos y su memoria editorial.";
                    default -> "Haz el balance de la semana del " + week + " de esta promoción: qué se publicó, qué falló y "
                            + "qué conviene cambiar. Sin inventar cifras.";
                };
                String taskId = orders.submit(promotionId, type, title, goal, List.of(week.toString()));
                done.save(new AiAutoOrderEntity(promotionId, type.name(), week, taskId, clock.instant()));
            });
            log.info("Encargo semanal creado: {} de {} (semana del {})", type, promotionId, week);
            n++;
        }
        return n;
    }
}
