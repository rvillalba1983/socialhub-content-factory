package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Propuestas de la semana: Claude propone las publicaciones de una semana y SocialHub las crea como «Propuesta» para
 * que Raúl apruebe o rechace cada una. Se comprueban todas antes de crear ninguna.
 */
@Component
@RequiredArgsConstructor
class WeeklyProposalsHandler implements AiTypeHandler {

    static final int MAX = 21;

    private final PromotionQueries promotions;
    private final PublishingQueries queries;
    private final PublishingService service;
    private final Clock clock;
    private final ExternalCalendar external;

    @Override public AiRequestType type() { return AiRequestType.WEEKLY_PROPOSALS; }

    /** La semana: la del primer dato de entrada si es una fecha; si no, la que empieza el lunes que viene. */
    LocalDate weekStart(List<String> inputs) {
        if (!inputs.isEmpty()) {
            try {
                return LocalDate.parse(inputs.getFirst()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            } catch (DateTimeParseException e) {
                // No es una fecha
            }
        }
        return LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        LocalDate from = weekStart(job.inputs());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("week", Map.of("from", from.toString(), "to", from.plusDays(6).toString()));
        inputs.put("promotion", promotions.summaryForAi(job.promotionId()));
        inputs.put("already_planned", queries.forAi(job.promotionId(), from, from.plusDays(6)));
        // Lo programado o publicado fuera de SocialHub (Publer, Publora o la red) también ocupa la semana
        List<Map<String, Object>> outside = external.forAi(job.promotionId(), from, from.plusDays(6));
        if (!outside.isEmpty()) inputs.put("planned_outside_socialhub", outside);
        inputs.put("expected_outputs", "proposals: [{scheduled_on (yyyy-mm-dd, dentro de la semana), scheduled_time (HH:mm), "
                + "platform (INSTAGRAM o TIKTOK), type (Carrusel, Historia, Reel…), title, idea, why}] (máximo " + MAX
                + ", siguiendo la cadencia de la promoción y sin repetir lo ya planificado) y summary.");
        return inputs;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode items = outputs.path("proposals");
        if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("Faltan las propuestas («outputs.proposals»)");
        if (items.size() > MAX) throw new IllegalArgumentException("Demasiadas propuestas (más de " + MAX + ")");
        LocalDate from = weekStart(job.inputs());
        LocalDate to = from.plusDays(6);
        List<Publication.Details> proposals = new ArrayList<>();
        int n = 0;
        for (JsonNode item : items) {
            n++;
            String where = "Propuesta " + n + ": ";
            LocalDate day;
            LocalTime time;
            Platform platform;
            try {
                day = LocalDate.parse(item.path("scheduled_on").asString(""));
                time = LocalTime.parse(item.path("scheduled_time").asString(""));
                platform = Platform.valueOf(item.path("platform").asString("").toUpperCase(Locale.ROOT));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                throw new IllegalArgumentException(where + "fecha, hora o plataforma no válidas");
            }
            if (day.isBefore(from) || day.isAfter(to)) {
                throw new IllegalArgumentException(where + "el " + day + " está fuera de la semana del " + from);
            }
            String type = item.path("type").asString("").strip();
            String title = item.path("title").asString("").strip();
            if (type.isEmpty() || title.isEmpty()) throw new IllegalArgumentException(where + "falta el tipo o el título");
            if (title.length() > 200) throw new IllegalArgumentException(where + "título demasiado largo");
            String idea = item.path("idea").asString("").strip();
            String why = item.path("why").asString("").strip();
            String note = (idea.isEmpty() ? "" : "Idea: " + idea) + (why.isEmpty() ? "" : (idea.isEmpty() ? "" : " · ") + "Por qué: " + why);
            proposals.add(new Publication.Details(platform, type.length() > 40 ? type.substring(0, 40) : type, title, day,
                    time.withSecond(0).withNano(0), "Europe/Madrid", "Publer",
                    note.isEmpty() ? null : (note.length() > 2000 ? note.substring(0, 2000) : note), null, null));
        }
        proposals.forEach(d -> service.create(job.promotionId(), d, PublicationStatus.PROPOSAL));
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return proposals.size() + (proposals.size() == 1 ? " propuesta" : " propuestas") + " para revisar en Publicaciones"
                + (summary.isEmpty() ? "" : " · " + summary);
    }
}
