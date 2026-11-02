package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiReports;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Balance semanal: el encargo lleva las publicaciones de la semana (lunes a domingo) y Claude devuelve el informe en
 * Markdown, que se guarda en Informes. Sin datos inventados: si faltan métricas, el informe lo dice.
 */
@Component
@RequiredArgsConstructor
class WeeklyBalanceHandler implements AiTypeHandler {

    private final PublishingQueries queries;
    private final AiReports reports;
    private final Clock clock;

    @Override public AiRequestType type() { return AiRequestType.WEEKLY_BALANCE; }

    /** La semana del balance: la del primer dato de entrada si es una fecha; si no, la semana pasada. */
    LocalDate weekStart(List<String> inputs) {
        if (!inputs.isEmpty()) {
            try {
                return LocalDate.parse(inputs.getFirst()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            } catch (DateTimeParseException e) {
                // No es una fecha
            }
        }
        return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        LocalDate from = weekStart(job.inputs());
        LocalDate to = from.plusDays(6);
        var list = queries.forAi(job.promotionId(), from, to);
        return Map.of("week", Map.of("from", from.toString(), "to", to.toString()), "publications", list,
                "expected_outputs", "report (Markdown) y summary. No inventes cifras: si faltan métricas, dilo.");
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        return reports.save(job, "balance-semanal-" + weekStart(job.inputs()), outputs, result);
    }
}
