package com.socialhub.publishing.application;

import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.application.ExternalCalendarService;
import com.socialhub.publishing.application.PublishingQueriesService;
import com.socialhub.publishing.application.PublishingService;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Propuestas de la semana: Claude propone las publicaciones de una semana y SocialHub las crea como «Propuesta» para
 * que Raúl apruebe o rechace cada una. Cada propuesta se comprueba por separado: las no válidas se descartan (y se cuentan en el resumen) y las buenas
 * entran. Si no queda ninguna, el encargo falla. Una lista vacía de Claude (con su motivo en el resumen) no es un fallo:
 * la tarea se completa como «Sin propuestas: …» y no se vuelve a pedir para la misma semana.
 */
@Component
@RequiredArgsConstructor
class WeeklyProposalsHandler implements AiTypeHandler {

    static final int MAX = 21;

    private final PromotionQueries promotions;
    private final PublishingQueriesService queries;
    private final PublishingService service;
    private final Clock clock;
    private final ExternalCalendarService external;
    private final ObjectMapper json;

    @Override public AiRequestType type() { return AiRequestType.WEEKLY_PROPOSALS; }

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Margen mínimo para poder preparar y programar una publicación. */
    static final Duration MIN_LEAD = Duration.ofHours(6);

    private static LocalDate nextMonday(LocalDate day) { return day.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); }

    /** La fecha del primer dato de entrada (su lunes), si es una fecha. */
    private static Optional<LocalDate> explicitWeek(List<String> inputs) {
        if (inputs.isEmpty()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(inputs.getFirst()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * La semana del encargo: la del primer dato de entrada si es una fecha (así se guarda al crearlo). Los encargos
     * antiguos, sin fecha, toman la semana que se envió en su último archivo de «para-claude» ({@code inputs.week.from});
     * si no se puede leer, el lunes siguiente al día en que se crearon, nunca el de «hoy».
     */
    LocalDate weekStart(AiJob job) {
        return explicitWeek(job.inputs()).or(() -> sentWeek(job.requestPath())).orElseGet(() -> {
            Instant created = job.createdAt() == null || job.createdAt().equals(Instant.EPOCH) ? clock.instant() : job.createdAt();
            return nextMonday(created.atZone(MADRID).toLocalDate());
        });
    }

    /**
     * El último día de la ventana: el segundo dato de entrada si es una fecha posterior al domingo (la primera ventana de una
     * promoción que empieza en un día sin cadencia se extiende hasta su primera fecha de cadencia) o, si no, el domingo.
     */
    LocalDate windowEnd(AiJob job) {
        LocalDate sunday = weekStart(job).plusDays(6);
        if (job.inputs().size() < 2) return sunday;
        try {
            LocalDate end = LocalDate.parse(job.inputs().get(1));
            return end.isAfter(sunday) ? end : sunday;
        } catch (DateTimeParseException e) {
            return sunday;
        }
    }

    /**
     * El primer día de la ventana: el tercer dato de entrada si es una fecha dentro de la semana (la ronda de W+1 empieza
     * después de la ventana ampliada de la primera semana) o, si no, el lunes.
     */
    LocalDate windowStart(AiJob job) {
        LocalDate monday = weekStart(job);
        if (job.inputs().size() < 3) return monday;
        try {
            LocalDate start = LocalDate.parse(job.inputs().get(2));
            return start.isAfter(monday) && !start.isAfter(monday.plusDays(6)) ? start : monday;
        } catch (DateTimeParseException e) {
            return monday;
        }
    }

    /** La semana (su lunes) escrita en un archivo de petición ya enviado, si existe, se lee y trae una fecha. */
    private Optional<LocalDate> sentWeek(@Nullable String requestPath) {
        if (requestPath == null || requestPath.isBlank()) return Optional.empty();
        try {
            Path file = Path.of(requestPath);
            if (!Files.isRegularFile(file)) return Optional.empty();
            String from = json.readTree(Files.readString(file)).path("inputs").path("week").path("from").asString("");
            return Optional.of(LocalDate.parse(from).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Al crear el encargo sin fecha, se escribe en sus datos el lunes que viene, para que no se recalcule nunca. */
    @Override
    public List<String> completeInputs(List<String> inputs) {
        if (explicitWeek(inputs).isPresent()) return inputs;
        List<String> complete = new ArrayList<>();
        complete.add(nextMonday(clock.instant().atZone(MADRID).toLocalDate()).toString());
        complete.addAll(inputs);
        return List.copyOf(complete);
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        LocalDate from = windowStart(job);
        Map<String, Object> inputs = new LinkedHashMap<>();
        LocalDate end = windowEnd(job);
        inputs.put("week", Map.of("from", from.toString(), "to", end.toString()));
        inputs.put("promotion", promotions.summaryForAi(job.promotionId()));
        inputs.put("already_planned", queries.forAi(job.promotionId(), from, end));
        // Lo programado o publicado fuera de SocialHub (Publer, Publora o la red) también ocupa la semana
        List<Map<String, Object>> outside = external.forAi(job.promotionId(), from, end);
        if (!outside.isEmpty()) inputs.put("planned_outside_socialhub", outside);
        inputs.put("expected_outputs", "proposals: [{scheduled_on (yyyy-mm-dd, dentro de la semana), scheduled_time (HH:mm), "
                + "platform (INSTAGRAM o TIKTOK), type (Carrusel, Historia, Reel…), title, idea, why}] (máximo " + MAX
                + ", siguiendo la cadencia de la promoción y sin repetir lo ya planificado) y summary.");
        return inputs;
    }

    /** Por qué se descarta una propuesta. El orden es el del resumen. */
    private enum Discard {
        INVALID("con fecha, hora, red, tipo o título no válidos"),
        OUT_OF_WEEK("fuera de la semana"),
        OUT_OF_PROMOTION("fuera de las fechas de la promoción"),
        TOO_SOON("con fecha pasada o a menos de 6 h");

        final String text;
        Discard(String text) { this.text = text; }
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode items = outputs.path("proposals");
        if (!items.isArray()) throw new IllegalArgumentException("Faltan las propuestas («outputs.proposals») o no son una lista");
        // Una lista vacía es una respuesta válida: Claude puede decidir que no toca publicar (p. ej., sin 6 h de margen)
        if (items.isEmpty()) {
            String reason = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
            return "Sin propuestas: " + (reason.isEmpty() ? "Claude no da el motivo" : reason);
        }
        if (items.size() > MAX) throw new IllegalArgumentException("Demasiadas propuestas (más de " + MAX + ")");
        LocalDate from = windowStart(job);
        LocalDate to = windowEnd(job);
        PromotionQueries.Dates dates = promotions.datesOf(job.promotionId());
        Instant earliest = clock.instant().plus(MIN_LEAD);
        List<Publication.Details> proposals = new ArrayList<>();
        Map<Discard, Integer> discarded = new EnumMap<>(Discard.class);
        for (JsonNode item : items) {
            Publication.Details details = null;
            Discard reason = null;
            try {
                LocalDate day = LocalDate.parse(item.path("scheduled_on").asString(""));
                LocalTime time = LocalTime.parse(item.path("scheduled_time").asString("")).withSecond(0).withNano(0);
                Platform platform = Platform.valueOf(item.path("platform").asString("").toUpperCase(Locale.ROOT));
                String type = item.path("type").asString("").strip();
                String title = item.path("title").asString("").strip();
                if (type.isEmpty() || title.isEmpty() || title.length() > 200) {
                    reason = Discard.INVALID;
                } else if (day.isBefore(from) || day.isAfter(to)) {
                    reason = Discard.OUT_OF_WEEK;
                } else if (day.isBefore(dates.startsOn()) || (dates.endsOn() != null && day.isAfter(dates.endsOn()))) {
                    reason = Discard.OUT_OF_PROMOTION;
                } else if (day.atTime(time).atZone(MADRID).toInstant().isBefore(earliest)) {
                    reason = Discard.TOO_SOON;
                } else {
                    String idea = item.path("idea").asString("").strip();
                    String why = item.path("why").asString("").strip();
                    String note = (idea.isEmpty() ? "" : "Idea: " + idea)
                            + (why.isEmpty() ? "" : (idea.isEmpty() ? "" : " · ") + "Por qué: " + why);
                    details = new Publication.Details(platform, type.length() > 40 ? type.substring(0, 40) : type, title, day,
                            time, "Europe/Madrid", "Publer",
                            note.isEmpty() ? null : (note.length() > 2000 ? note.substring(0, 2000) : note), null, null);
                }
            } catch (DateTimeParseException | IllegalArgumentException e) {
                reason = Discard.INVALID;
            }
            if (details != null) proposals.add(details);
            else discarded.merge(reason, 1, Integer::sum);
        }
        int total = discarded.values().stream().mapToInt(Integer::intValue).sum();
        String why = discarded.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().text).collect(Collectors.joining(", "));
        if (proposals.isEmpty()) {
            throw new IllegalArgumentException("Ninguna propuesta es válida · " + total
                    + (total == 1 ? " descartada: " : " descartadas: ") + why);
        }
        proposals.forEach(d -> service.create(job.promotionId(), d, PublicationStatus.PROPOSAL));
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return proposals.size() + (proposals.size() == 1 ? " propuesta" : " propuestas") + " para revisar en Publicaciones"
                + (total == 0 ? "" : " · " + total + (total == 1 ? " descartada: " : " descartadas: ") + why)
                + (summary.isEmpty() ? "" : " · " + summary);
    }
}
