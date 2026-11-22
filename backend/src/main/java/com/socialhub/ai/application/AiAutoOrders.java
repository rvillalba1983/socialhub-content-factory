package com.socialhub.ai.application;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiAutoOrder;
import com.socialhub.ai.domain.AiAutoOrderRepository;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudePickups;
import com.socialhub.promotion.PromotionActivation;
import com.socialhub.promotion.PromotionQueries;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Esa primera semana no se pide si ya no queda hueco válido (su último día acaba antes de la recogida más el margen mínimo de publicación).
 * Cada encargo semanal se crea una sola vez por promoción y semana. Los demás tipos se piden a mano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiAutoOrders {

    static final LocalTime FROM = LocalTime.of(9, 0);
    /** Registro (en {@code ai_auto_order}, con el último día de la ventana en la fecha) de una primera ventana ampliada. */
    static final String FIRST_WINDOW_END = "FIRST_WINDOW_END";

    private final PromotionQueries promotions;
    private final PromotionActivation promotionService;
    private final AiOrders orders;
    private final AiHandlers handlers;
    private final AiAutoOrderRepository done;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final com.socialhub.settings.PublishSettings settings;

    /** @return cuántos encargos se han creado */
    int createDue() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int n = 0;
        for (String id : promotionService.activateStarted(now.toLocalDate())) {
            log.info("Promoción {} activada sola: empieza hoy", id);
            // Se anota el día (hora de España) para avisarlo en «Qué hacer ahora» ese día
            LocalDate day = now.withZoneSameInstant(ClaudePickups.ZONE).toLocalDate();
            if (!done.exists(new AiAutoOrder.Key(id, AiAutoStartedService.TYPE, day))) {
                done.save(new AiAutoOrder(id, AiAutoStartedService.TYPE, day, null, clock.instant()));
            }
        }
        n += createSpecBatches(now);
        Optional<LocalDate> balance = balanceWeek(now);
        if (balance.isPresent()) n += create(AiRequestType.WEEKLY_BALANCE, balance.get());
        // Primero la primera semana: si su ventana se extiende, la ronda de W+1 empieza después
        n += createFirstWeeks(now);
        Optional<LocalDate> proposals = proposalsWeek(now);
        if (proposals.isPresent()) n += create(AiRequestType.WEEKLY_PROPOSALS, proposals.get());
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
        Optional<FirstWeek> first = firstWeek(now, startsOn, endsOn, promotions.cadenceOf(promotionId), settings.minMargin());
        if (first.isEmpty()) return FirstWeekResult.NOT_DUE;
        if (!hasRoom(now, first.get().to(), settings.minMargin())) return FirstWeekResult.NO_ROOM;
        LocalDate week = first.get().week(), from = first.get().from(), to = first.get().to();
        var key = new AiAutoOrder.Key(promotionId, AiRequestType.WEEKLY_PROPOSALS.name(), week);
        if (done.exists(key)) return FirstWeekResult.ALREADY_REQUESTED;
        transactions.executeWithoutResult(s -> {
            String title = "Propuestas de la primera semana · del " + from.getDayOfMonth() + " al " + to.getDayOfMonth()
                    + " " + to.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
            String goal = "La promoción empieza ahora: propón sus publicaciones del " + from + " al " + to
                    + ", según su cadencia, sus objetivos, sus hitos y su memoria editorial. No propongas nada antes del "
                    + from + ".";
            String taskId = orders.submitInternal(promotionId, AiRequestType.WEEKLY_PROPOSALS, title, goal,
                    to.isAfter(week.plusDays(6)) ? List.of(week.toString(), to.toString()) : List.of(week.toString()));
            done.save(new AiAutoOrder(promotionId, AiRequestType.WEEKLY_PROPOSALS.name(), week, taskId, clock.instant()));
            // Hasta dónde llegó su ventana: la ronda de W+1 empieza el día siguiente si la ventana invadió esa semana
            if (to.isAfter(week.plusDays(6))) {
                done.save(new AiAutoOrder(promotionId, FIRST_WINDOW_END, to, taskId, clock.instant()));
            }
        });
        log.info("Propuestas de la primera semana pedidas: {} (del {} al {})", promotionId, from, to);
        return FirstWeekResult.REQUESTED;
    }

    /**
     * Si queda hueco válido para proponer: con la recogida que le tocaría al encargo pedido ahora, alguna propuesta tiene
     * que poder estar al «margen mínimo de publicación» ({@code lead}, el mismo que exige el manejador al aplicar el resultado)
     * o más de la recogida y dentro del último día. Si no (p. ej. domingo por la noche con solo ese
     * día por delante), el encargo fallaría siempre y se reintentaría gastando encargos.
     */
    static boolean hasRoom(ZonedDateTime now, LocalDate lastDay, Duration lead) {
        ZonedDateTime pickup = ClaudePickups.pickupFor(now.toInstant().plus(ClaudePickups.DISPATCH_MAX), false);
        ZonedDateTime endOfLastDay = lastDay.plusDays(1).atStartOfDay(now.getZone());
        return !endOfLastDay.toInstant().isBefore(pickup.toInstant().plus(lead));
    }

    /** Primera semana de una promoción: su lunes y los días que quedan por proponer. */
    record FirstWeek(LocalDate week, LocalDate from, LocalDate to) {}

    /**
     * La primera semana toca pedirla ya si pasó el lunes anterior a las 18:00 (cuando se habría pedido sola) y la semana
     * no ha terminado.
     */
    static Optional<FirstWeek> firstWeek(ZonedDateTime now, LocalDate startsOn, LocalDate endsOn, Duration lead) {
        return firstWeek(now, startsOn, endsOn, null, lead);
    }

    private static final Pattern CADENCE = Pattern.compile(
            "(?<![\\p{L}])([LMXJVSD](?:\\s*[/,\\-]\\s*[LMXJVSD])*)(?![\\p{L}])\\s*(?:a\\s+las?\\s+)?(\\d{1,2}):(\\d{2})",
            Pattern.CASE_INSENSITIVE);
    private static final String DAY_LETTERS = "LMXJVSD";

    /**
     * Los huecos de la cadencia («L/X/V 19:00 · historias M/J 13:00», «x/v a las 19:00»): día de la semana y hora. Un texto
     * sin días y hora reconocibles no da ningún hueco.
     */
    static List<java.util.Map.Entry<DayOfWeek, LocalTime>> cadenceSlots(@org.jspecify.annotations.Nullable String cadence) {
        List<java.util.Map.Entry<DayOfWeek, LocalTime>> slots = new ArrayList<>();
        if (cadence == null) return slots;
        Matcher m = CADENCE.matcher(cadence);
        while (m.find()) {
            int hour = Integer.parseInt(m.group(2)), minute = Integer.parseInt(m.group(3));
            if (hour > 23 || minute > 59) continue;
            LocalTime time = LocalTime.of(hour, minute);
            for (char c : m.group(1).toUpperCase(Locale.ROOT).toCharArray()) {
                int i = DAY_LETTERS.indexOf(c);
                if (i >= 0) slots.add(java.util.Map.entry(DayOfWeek.of(i + 1), time));
            }
        }
        return slots;
    }

    /**
     * La primera fecha de la cadencia con margen: el primer hueco desde hoy (o desde el inicio, si es posterior) que queda a
     * {@code lead} (el margen mínimo de publicación) o más de la recogida, dentro de la promoción. Vacío si la cadencia no se entiende o no hay ninguno.
     */
    static Optional<LocalDate> firstCadenceDate(ZonedDateTime now, LocalDate startsOn, LocalDate endsOn,
                                                @org.jspecify.annotations.Nullable String cadence, Duration lead) {
        List<java.util.Map.Entry<DayOfWeek, LocalTime>> slots = cadenceSlots(cadence);
        if (slots.isEmpty()) return Optional.empty();
        ZoneId zone = now.getZone();
        java.time.Instant earliest = ClaudePickups.pickupFor(now.toInstant().plus(ClaudePickups.DISPATCH_MAX), false)
                .toInstant().plus(lead);
        LocalDate day = startsOn.isAfter(now.toLocalDate()) ? startsOn : now.toLocalDate();
        for (; !day.isAfter(endsOn); day = day.plusDays(1)) {
            for (var slot : slots) {
                if (slot.getKey() == day.getDayOfWeek() && !day.atTime(slot.getValue()).atZone(zone).toInstant().isBefore(earliest)) {
                    return Optional.of(day);
                }
            }
            if (day.isAfter(now.toLocalDate().plusDays(60))) break;
        }
        return Optional.empty();
    }

    /**
     * Igual, pero si la promoción empieza en un día sin cadencia (o sin margen) y su primera fecha con margen cae después del
     * domingo, la ventana se extiende hasta esa fecha (sin pasar del fin de la promoción).
     */
    static Optional<FirstWeek> firstWeek(ZonedDateTime now, LocalDate startsOn, LocalDate endsOn,
                                         @org.jspecify.annotations.Nullable String cadence, Duration lead) {
        Optional<FirstWeek> normal = firstWeekBase(now, startsOn, endsOn);
        if (normal.isEmpty()) return normal;
        FirstWeek w = normal.get();
        if (!w.to().equals(w.week().plusDays(6))) return normal; // la promoción acaba esa semana
        Optional<LocalDate> first = firstCadenceDate(now, w.from(), endsOn, cadence, lead);
        if (first.isEmpty() || !first.get().isAfter(w.to())) return normal;
        return Optional.of(new FirstWeek(w.week(), w.from(), first.get()));
    }

    private static Optional<FirstWeek> firstWeekBase(ZonedDateTime now, LocalDate startsOn, LocalDate endsOn) {
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
                transactions.executeWithoutResult(s -> orders.submitInternal(promotionId, AiRequestType.WEEKLY_SPEC, title, goal, batch));
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
        var key = new AiAutoOrder.Key(promotionId, type.name(), week);
        if (done.exists(key)) return WeekResult.ALREADY_REQUESTED;
        // Sin nada que hacer todavía (p. ej., sin publicaciones aprobadas): no se crea ni se marca como hecho
        if (!handlers.of(type).map(h -> h.readyForAutoOrder(promotionId, week)).orElse(true)) return WeekResult.NOT_READY;
        // La ventana ampliada de la primera semana ya cubre esos días de W+1: la ronda semanal empieza el día siguiente
        LocalDate start = week;
        if (type == AiRequestType.WEEKLY_PROPOSALS) {
            LocalDate covered = done.ofPromotionAndType(promotionId, FIRST_WINDOW_END).stream()
                    .map(AiAutoOrder::weekStart).filter(d -> !d.isBefore(week)).max(LocalDate::compareTo).orElse(null);
            if (covered != null) start = covered.plusDays(1);
            // Nada que proponer: la primera ventana ya llega hasta el final de W+1
            if (start.isAfter(week.plusDays(6))) return WeekResult.NOT_READY;
        }
        LocalDate from = start;
        transactions.executeWithoutResult(s -> {
            String title = type.label() + " · semana del " + week.getDayOfMonth() + " "
                    + week.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
            String goal = switch (type) {
                case WEEKLY_PROPOSALS -> "Propón las publicaciones de la semana del " + week + " para esta promoción"
                        + (from.isAfter(week) ? " (desde el " + from + ": los días anteriores ya los cubre la primera semana)" : "")
                        + ", según su cadencia, sus objetivos, sus hitos y su memoria editorial.";
                default -> "Haz el balance de la semana del " + week + " de esta promoción: qué se publicó, qué falló y "
                        + "qué conviene cambiar. Sin inventar cifras.";
            };
            List<String> inputs = from.isAfter(week) ? List.of(week.toString(), week.plusDays(6).toString(), from.toString())
                    : List.of(week.toString());
            String taskId = orders.submitInternal(promotionId, type, title, goal, inputs);
            done.save(new AiAutoOrder(promotionId, type.name(), week, taskId, clock.instant()));
        });
        log.info("Encargo semanal creado: {} de {} (semana del {})", type, promotionId, week);
        return WeekResult.REQUESTED;
    }
}
