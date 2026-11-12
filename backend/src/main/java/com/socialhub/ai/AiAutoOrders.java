package com.socialhub.ai;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionService;
import java.time.Clock;
import java.time.Duration;
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
 *   <li>spec.json por lotes (no por semana): por promoción, en cuanto hay 3 o más publicaciones pendientes de contenido, o
 *       la más próxima de ellas sale en menos de 3 días (aunque sean 1 o 2). Cada publicación entra en un solo spec
 *       abierto. Sin publicaciones pendientes no se pide nada. Lo decide el manejador del tipo ({@link AiTypeHandler#autoOrderBatch}).</li>
 *   <li>Lunes desde las 9:00: balance de la semana pasada. Si estaba cerrado, se pide el martes o el miércoles.</li>
 *   <li>Propuestas de la semana siguiente (W+1): desde el lunes de la semana en curso (W) a las 18:00 hasta el domingo de
 *       W a las 23:59. Si SocialHub estaba cerrado el lunes, se piden cualquier día después, hasta el domingo. Así, una
 *       promoción guardada de jueves a domingo, o cuya primera semana ya no tenía margen, también las tiene. Las de W+1
 *       se piden una sola vez por promoción (registro de lo ya pedido), pidan lo que las pida (ronda o guardado).</li>
 * </ul>
 * Las propuestas se piden también para las promociones planificadas (se activan solas el día que empiezan); el spec y
 * el balance, solo para las activas.
 * Antes de nada, las promociones planificadas que ya han empezado pasan a activas.
 * Además, una promoción nueva pide enseguida las propuestas de su primera semana si ya pasó el lunes en que se habrían
 * pedido (si empieza a mitad de semana, para los días que quedan).
 * Esa primera semana no se pide si ya no queda hueco válido (su último día acaba antes de la recogida más 6 h).
 * Cada encargo semanal se crea una sola vez por promoción y semana. Los demás tipos se piden a mano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiAutoOrders {

    static final LocalTime FROM = LocalTime.of(9, 0);

    private final PromotionQueries promotions;
    private final PromotionService promotionService;
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
        for (String id : promotionService.activateStarted(now.toLocalDate())) {
            log.info("Promoción {} activada sola: empieza hoy", id);
            // Se anota el día (hora de España) para avisarlo en «Qué hacer ahora» ese día
            LocalDate day = now.withZoneSameInstant(ClaudePickups.ZONE).toLocalDate();
            if (!done.existsById(new AiAutoOrderEntity.Key(id, AiAutoStarted.TYPE, day))) {
                done.save(new AiAutoOrderEntity(id, AiAutoStarted.TYPE, day, null, clock.instant()));
            }
        }
        n += createSpecBatches(now);
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
        for (PromotionQueries.PromotionDates p : promotions.proposableDates()) {
            if (requestFirstWeek(p.id(), p.startsOn(), p.endsOn(), now) == FirstWeekResult.REQUESTED) n++;
        }
        return n;
    }

    /** Qué ha pasado al intentar pedir las propuestas de la primera semana. */
    enum FirstWeekResult { REQUESTED, ALREADY_REQUESTED, NOT_DUE, NO_ROOM }

    /**
     * Pide las propuestas de la primera semana de una promoción si ya toca y no estaban pedidas. Lo usan la ronda y el
     * guardado de la promoción, y comparten el registro de lo ya pedido: no se pide dos veces.
     */
    synchronized FirstWeekResult requestFirstWeek(String promotionId, LocalDate startsOn, LocalDate endsOn, ZonedDateTime now) {
        Optional<FirstWeek> first = firstWeek(now, startsOn, endsOn);
        if (first.isEmpty()) return FirstWeekResult.NOT_DUE;
        if (!hasRoom(now, first.get().to())) return FirstWeekResult.NO_ROOM;
        LocalDate week = first.get().week(), from = first.get().from(), to = first.get().to();
        var key = new AiAutoOrderEntity.Key(promotionId, AiRequestType.WEEKLY_PROPOSALS.name(), week);
        if (done.existsById(key)) return FirstWeekResult.ALREADY_REQUESTED;
        transactions.executeWithoutResult(s -> {
            String title = "Propuestas de la primera semana · del " + from.getDayOfMonth() + " al " + to.getDayOfMonth()
                    + " " + to.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
            String goal = "La promoción empieza ahora: propón sus publicaciones del " + from + " al " + to
                    + ", según su cadencia, sus objetivos, sus hitos y su memoria editorial. No propongas nada antes del "
                    + from + ".";
            String taskId = orders.submit(promotionId, AiRequestType.WEEKLY_PROPOSALS, title, goal, List.of(week.toString()));
            done.save(new AiAutoOrderEntity(promotionId, AiRequestType.WEEKLY_PROPOSALS.name(), week, taskId, clock.instant()));
        });
        log.info("Propuestas de la primera semana pedidas: {} (del {} al {})", promotionId, from, to);
        return FirstWeekResult.REQUESTED;
    }

    /** Margen mínimo entre la recogida y la hora de una propuesta (el mismo que exige el manejador al aplicar el resultado). */
    static final Duration MIN_LEAD = Duration.ofHours(6);

    /**
     * Si queda hueco válido para proponer: con la recogida que le tocaría al encargo pedido ahora, alguna propuesta tiene
     * que poder estar a 6 h o más de la recogida y dentro del último día. Si no (p. ej. domingo por la noche con solo ese
     * día por delante), el encargo fallaría siempre y se reintentaría gastando encargos.
     */
    static boolean hasRoom(ZonedDateTime now, LocalDate lastDay) {
        ZonedDateTime pickup = ClaudePickups.pickupFor(now.toInstant().plus(ClaudePickups.DISPATCH_MAX), false);
        ZonedDateTime endOfLastDay = lastDay.plusDays(1).atStartOfDay(now.getZone());
        return !endOfLastDay.toInstant().isBefore(pickup.toInstant().plus(MIN_LEAD));
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

    /**
     * Pide el spec de cada promoción activa o planificada (una planificada no espera al día en que se activa) que tenga un lote
     * pendiente ({@link AiTypeHandler#autoOrderBatch}). Los
     * encargos abiertos ya cuentan para no repetir publicaciones, así que no hace falta registro de lo ya pedido.
     */
    synchronized int createSpecBatches(ZonedDateTime now) {
        var handler = handlers.of(AiRequestType.WEEKLY_SPEC);
        if (handler.isEmpty()) return 0;
        int n = 0;
        LocalDate today = now.toLocalDate();
        for (String promotionId : promotions.proposableBetween(today, today.plusYears(50))) {
            try {
                List<String> batch = handler.get().autoOrderBatch(promotionId, now.toInstant());
                if (batch.isEmpty()) continue;
                String title = AiRequestType.WEEKLY_SPEC.label() + " · " + batch.size()
                        + (batch.size() == 1 ? " publicación" : " publicaciones");
                String goal = "Prepara el spec.json de piezas de estas " + batch.size() + " publicaciones de la promoción "
                        + "(las de «inputs.publications», con sus fechas), con el material de sus carpetas y sus normas.";
                transactions.executeWithoutResult(s -> orders.submit(promotionId, AiRequestType.WEEKLY_SPEC, title, goal, batch));
                log.info("Encargo de spec por lotes creado: {} ({} publicaciones)", promotionId, batch.size());
                n++;
            } catch (RuntimeException e) {
                log.warn("Spec por lotes omitido para {}: {}", promotionId, e.toString());
            }
        }
        return n;
    }

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

    /**
     * Lunes de la semana cuyas propuestas toca pedir ahora (la siguiente, W+1), si toca alguna: desde el lunes de la
     * semana en curso a las 18:00 hasta el domingo a las 23:59. El lunes antes de las 18:00 no toca ninguna (las de esa
     * semana se pidieron la semana anterior).
     */
    static Optional<LocalDate> proposalsWeek(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate next = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        if (today.getDayOfWeek() == DayOfWeek.MONDAY && now.toLocalTime().isBefore(PROPOSALS_FROM)) return Optional.empty();
        return Optional.of(next);
    }

    /** Qué ha pasado al intentar pedir un encargo semanal. */
    enum WeekResult { REQUESTED, ALREADY_REQUESTED, NOT_READY }

    /**
     * Pide las propuestas de la semana W+1 de una promoción si toca ahora y no estaban pedidas. Lo usan el guardado de la
     * promoción y (por el mismo camino, {@link #requestWeek}) la ronda; comparten el registro de lo ya pedido.
     */
    synchronized WeekResult requestNextWeekProposals(String promotionId, LocalDate week) {
        return requestWeek(promotionId, AiRequestType.WEEKLY_PROPOSALS, week);
    }

    private synchronized int create(AiRequestType type, LocalDate week) {
        int n = 0;
        // Las propuestas también para las planificadas; el spec y el balance, solo para las activas
        List<String> active = type == AiRequestType.WEEKLY_PROPOSALS
                ? promotions.proposableBetween(week, week.plusDays(6)) : promotions.activeBetween(week, week.plusDays(6));
        for (String promotionId : active) {
            if (requestWeek(promotionId, type, week) == WeekResult.REQUESTED) n++;
        }
        return n;
    }

    /** Crea el encargo semanal de una promoción si no estaba ya pedido (y hay algo que hacer). */
    private synchronized WeekResult requestWeek(String promotionId, AiRequestType type, LocalDate week) {
        var key = new AiAutoOrderEntity.Key(promotionId, type.name(), week);
        if (done.existsById(key)) return WeekResult.ALREADY_REQUESTED;
        // Sin nada que hacer todavía (p. ej., sin publicaciones aprobadas): no se crea ni se marca como hecho
        if (!handlers.of(type).map(h -> h.readyForAutoOrder(promotionId, week)).orElse(true)) return WeekResult.NOT_READY;
        transactions.executeWithoutResult(s -> {
            String title = type.label() + " · semana del " + week.getDayOfMonth() + " "
                    + week.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
            String goal = switch (type) {
                case WEEKLY_PROPOSALS -> "Propón las publicaciones de la semana del " + week + " para esta promoción, "
                        + "según su cadencia, sus objetivos, sus hitos y su memoria editorial.";
                default -> "Haz el balance de la semana del " + week + " de esta promoción: qué se publicó, qué falló y "
                        + "qué conviene cambiar. Sin inventar cifras.";
            };
            String taskId = orders.submit(promotionId, type, title, goal, List.of(week.toString()));
            done.save(new AiAutoOrderEntity(promotionId, type.name(), week, taskId, clock.instant()));
        });
        log.info("Encargo semanal creado: {} de {} (semana del {})", type, promotionId, week);
        return WeekResult.REQUESTED;
    }
}
