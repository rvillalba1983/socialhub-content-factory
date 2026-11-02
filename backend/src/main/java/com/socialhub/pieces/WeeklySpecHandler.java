package com.socialhub.pieces;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.JsonContracts;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import tools.jackson.databind.ObjectMapper;

/**
 * spec.json de la semana: Claude devuelve el spec en {@code outputs.spec} (contrato promo-spec.v1). SocialHub lo
 * comprueba, lo guarda en {@code <Encargos de la promoción>/specs-de-claude} y encarga él mismo las piezas. Esa
 * subcarpeta no la vigila nadie, así que el spec no se encarga dos veces.
 */
@Component
@RequiredArgsConstructor
class WeeklySpecHandler implements AiTypeHandler {

    static final String SPECS = "specs-de-claude";

    private final PromotionQueries promotions;
    private final PublishingQueries publications;
    private final PieceOrders orders;
    private final SpecReader reader;
    private final PiecePublications piecePublications;
    private final JsonContracts contracts;
    private final ObjectMapper json;
    private final Clock clock;

    @Override public AiRequestType type() { return AiRequestType.WEEKLY_SPEC; }

    /** La semana del spec: la del primer dato de entrada si es una fecha; si no, la que viene. */
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
        LocalDate to = from.plusDays(6);
        var list = publications.forAi(job.promotionId(), from, to);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("week", Map.of("from", from.toString(), "to", to.toString()));
        inputs.put("folders", promotions.folderMap(job.promotionId()));
        inputs.put("publications", list);
        inputs.put("expected_outputs", "spec (objeto con el formato de contracts/promo-spec.v1.schema.json) y summary. "
                + "Usa rutas completas de este ordenador, dentro de las carpetas de «folders». Cada pieza que corresponda "
                + "a una publicación lleva «publicacion» con su id.");
        return inputs;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode spec = outputs.path("spec");
        if (!spec.isObject()) throw new IllegalArgumentException("Falta el spec («outputs.spec»)");
        List<String> errors = contracts.validate(JsonContracts.PROMO_SPEC, spec);
        if (!errors.isEmpty()) throw new IllegalArgumentException("El spec no cumple promo-spec.v1: " + String.join("; ", errors));
        String folder = promotions.ordersFolder(job.promotionId())
                .orElseThrow(() -> new IllegalArgumentException("La promoción no tiene carpeta de «Encargos»"));
        Path file;
        try {
            Path dir = Files.createDirectories(Path.of(folder).resolve(SPECS));
            file = fresh(dir, "spec-" + weekStart(job.inputs()));
            Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el spec: " + e.getMessage(), e);
        }
        // Se comprueba aquí, fuera de la transacción del encargo de piezas: un fallo dentro la dejaría marcada para
        // deshacerse y arrastraría la del resultado de Claude
        try {
            SpecReader.Loaded loaded = reader.load(file.toString(), promotions.driveFolders(job.promotionId()));
            piecePublications.check(job.promotionId(), loaded.spec());
        } catch (IllegalArgumentException e) {
            reject(file);
            throw new IllegalArgumentException("El spec no se puede encargar: " + e.getMessage());
        }
        String taskId = orders.order(job.promotionId(), file.toString());
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return "spec guardado en " + file + " · piezas encargadas (tarea " + taskId + ")"
                + (summary.isEmpty() ? "" : " · " + summary);
    }

    /** Un spec que no se puede encargar se queda, con otro nombre, para poder revisarlo. */
    private static void reject(Path file) {
        try {
            Files.move(file, fresh(file.getParent(), file.getFileName().toString().replace(".json", "") + ".rechazado"));
        } catch (IOException e) {
            // Se queda con su nombre: nadie vigila esta carpeta
        }
    }

    private static Path fresh(Path dir, String name) {
        boolean rejected = name.endsWith(".rechazado");
        String base = rejected ? name.substring(0, name.length() - ".rechazado".length()) : name;
        String ext = rejected ? ".rechazado" : ".json";
        Path candidate = dir.resolve(base + ext);
        for (int n = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); n++) candidate = dir.resolve(base + "_" + n + ext);
        return candidate;
    }
}
