package com.socialhub.ai;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionSavedHook;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Al guardar una promoción: si ya toca pedir las propuestas de su primera semana, se piden en ese momento con la misma
 * lógica y el mismo registro que la ronda de {@link AiAutoOrders}, y se devuelve el aviso que enseña el guardado.
 * Si su primera semana no tiene margen (NO_ROOM) o se guarda de jueves a domingo, y la promoción cubre la semana
 * siguiente, se piden también las propuestas de esa semana (sin duplicar lo que ya pidió la ronda).
 * Se llama cuando el guardado ya está confirmado, así que un fallo al guardar no deja ningún encargo huérfano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiProposalsOnSave implements PromotionSavedHook {

    private final AiAutoOrders autoOrders;
    private final PromotionQueries promotions;
    private final AiSettings settings;
    private final AppModeHolder mode;
    private final Clock clock;

    @Override
    public Optional<String> afterSave(PromotionSavedHook.Saved promotion) {
        if (mode.isDemo()) return Optional.empty();
        try {
            return note(promotion);
        } catch (RuntimeException e) {
            // El guardado ya está hecho: un fallo aquí no puede deshacerlo; la ronda lo reintentará
            log.warn("No se han podido pedir las propuestas de {} al guardarla: {}", promotion.id(), e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> note(PromotionSavedHook.Saved promotion) {
        if (!"ACTIVE".equals(promotion.status()) && !"PLANNED".equals(promotion.status())) return Optional.empty();
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ClaudePickups.ZONE);
        LocalDate today = now.toLocalDate();
        if (promotion.endsOn().isBefore(today)) return Optional.empty();
        LocalDate week = promotion.startsOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate askOn = week.minusWeeks(1);
        if (now.isBefore(askOn.atTime(AiAutoOrders.PROPOSALS_FROM).atZone(ClaudePickups.ZONE))) {
            return Optional.of("Sus propuestas se pedirán " + mondayAt18(askOn));
        }
        var first = autoOrders.requestFirstWeek(promotion.id(), promotion.startsOn(), promotion.endsOn(), now);
        // La semana siguiente (W+1) también se pide en el acto si la primera no tiene margen o si se guarda de jueves a
        // domingo; los demás días la pedirá la ronda sola. Mismo registro que la ronda: no se pide dos veces.
        boolean noRoom = first == AiAutoOrders.FirstWeekResult.NO_ROOM;
        AiAutoOrders.WeekResult next = null;
        LocalDate nextWeek = AiAutoOrders.proposalsWeek(now).orElse(null);
        boolean thursdayOrLater = today.getDayOfWeek().getValue() >= DayOfWeek.THURSDAY.getValue();
        if (nextWeek != null && (noRoom || thursdayOrLater) && !promotion.startsOn().isAfter(nextWeek.plusDays(6))
                && !promotion.endsOn().isBefore(nextWeek)) {
            next = autoOrders.requestNextWeekProposals(promotion.id(), nextWeek);
        }
        java.util.List<String> asked = new java.util.ArrayList<>();
        if (first == AiAutoOrders.FirstWeekResult.REQUESTED) asked.add("de la primera semana");
        if (next == AiAutoOrders.WeekResult.REQUESTED) asked.add("de la semana del " + dayMonth(nextWeek));
        String lead = noRoom ? "No queda margen para lo que resta de esta semana. " : "";
        if (asked.isEmpty()) {
            if (noRoom && next == AiAutoOrders.WeekResult.ALREADY_REQUESTED) {
                return Optional.of(lead + "Las propuestas de la semana del " + dayMonth(nextWeek) + " ya estaban pedidas.");
            }
            return noRoom ? Optional.of(lead.trim()) : Optional.empty();
        }
        String why = blocker(promotion);
        String what = lead + "Propuestas " + String.join(" y ", asked) + " pedidas";
        if (why != null) return Optional.of(what + ", pero no pueden salir: " + why);
        return Optional.of(what + ": llegarán " + ClaudePickups.etaText(now.toInstant(), false));
    }

    /** «16 de nov». */
    private static String dayMonth(LocalDate day) {
        return day.getDayOfMonth() + " de " + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
    }

    /** «el lunes 9 de nov a las 18:00». */
    private static String mondayAt18(LocalDate monday) {
        return "el lunes " + monday.getDayOfMonth() + " de "
                + monday.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES")) + " a las "
                + AiAutoOrders.PROPOSALS_FROM.getHour() + ":00";
    }

    /** Lo que ya se sabe que impedirá que el encargo salga, en una frase; nulo si nada lo impide. */
    private @org.jspecify.annotations.Nullable String blocker(PromotionSavedHook.Saved promotion) {
        Optional<String> folder = promotions.brandOrdersFolder(promotion.brandId());
        if (folder.isEmpty()) return "la marca no tiene carpeta de «Encargos»";
        if (!AiFolders.available(folder.get())) {
            return "la carpeta de «Encargos» no está disponible en este ordenador (¿está abierto Google Drive?)";
        }
        if (settings.usedToday() >= settings.dailyLimit()) {
            return "se ha alcanzado el límite diario de encargos a Claude (" + settings.dailyLimit() + "), saldrán mañana";
        }
        return null;
    }
}
