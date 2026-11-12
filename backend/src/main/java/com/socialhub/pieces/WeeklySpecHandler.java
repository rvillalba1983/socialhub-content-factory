package com.socialhub.pieces;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.JsonContracts;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.production.ProductionService;
import com.socialhub.publishing.PublishingQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * spec.json por lotes: Claude devuelve el spec en {@code outputs.spec} (contrato promo-spec.v1). SocialHub lo
 * comprueba, lo guarda en {@code <Encargos de la promoción>/specs-de-claude} y encarga él mismo las piezas. Esa
 * subcarpeta no la vigila nadie, así que el spec no se encarga dos veces.
 * <p>
 * El lote son las publicaciones pendientes de contenido de la promoción: «En producción», sin piezas (archivos), con la
 * hora de publicación por delante y que no estén ya en un spec abierto o completado. Se pide en cuanto hay 3 o más, o
 * cuando la más próxima sale en menos de 3 días (aunque sean 1 o 2). El encargo lleva los identificadores de esas
 * publicaciones como datos de entrada, así que cada publicación está en un solo spec; si el spec falla del todo
 * (sin reintentos) o se cancela, sus publicaciones vuelven a contar como pendientes, pero ese mismo lote no se pide solo
 * otra vez hasta pasadas 24 horas (a mano sí). Los specs antiguos, con una fecha como dato, cubren las publicaciones de
 * esa semana (de lunes a domingo).
 */
@Component
@RequiredArgsConstructor
class WeeklySpecHandler implements AiTypeHandler {

    static final String SPECS = "specs-de-claude";
    /** Con tantas publicaciones pendientes (o más) se pide el spec. */
    static final int MIN_BATCH = 3;
    /** O cuando la más próxima de las pendientes sale en menos de este tiempo. */
    static final java.time.Duration SOON = java.time.Duration.ofDays(3);

    private final PromotionQueries promotions;
    private final PublishingQueries publications;
    private final ProductionService production;
    private final PieceOrders orders;
    private final SpecReader reader;
    private final PiecePublications piecePublications;
    private final JsonContracts contracts;
    private final ObjectMapper json;
    private final Clock clock;

    @Override public AiRequestType type() { return AiRequestType.WEEKLY_SPEC; }

    /** Hay que pedir ya el spec: 3 o más pendientes, o la más próxima sale en menos de 3 días. */
    static boolean due(List<Instant> pendingAt, Instant now) {
        if (pendingAt.isEmpty()) return false;
        Instant first = pendingAt.stream().min(java.util.Comparator.naturalOrder()).orElseThrow();
        return pendingAt.size() >= MIN_BATCH || first.isBefore(now.plus(SOON));
    }

    /** Un lote que falló del todo, y cuándo. */
    private record FailedLot(Set<String> ids, Instant at) {}

    /**
     * Lo que ya ocupan los specs de la promoción: publicaciones en un spec abierto, en uno abierto o completado, semanas
     * enteras de los specs antiguos (que llevaban una fecha como dato) y lotes que fallaron del todo.
     */
    private record Covered(Set<String> open, Set<String> all, Set<LocalDate> weeks, List<FailedLot> failed) {
        boolean covers(PublishingQueries.AwaitingContent p) {
            return all.contains(p.id()) || weeks.contains(mondayOf(p.scheduledAt()));
        }
    }

    private static final java.time.ZoneId SPAIN = java.time.ZoneId.of("Europe/Madrid");
    /** Un lote que falló del todo no se vuelve a pedir solo, igual, hasta pasado este tiempo (a mano sí se puede). */
    static final java.time.Duration RETRY_LOT_AFTER = java.time.Duration.ofHours(24);

    private static LocalDate mondayOf(Instant at) {
        return mondayOf(at.atZone(SPAIN).toLocalDate());
    }

    private static LocalDate mondayOf(LocalDate day) {
        return day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    /** @param except tarea que no cuenta (la que se está preparando) */
    private Covered covered(String promotionId, @org.jspecify.annotations.Nullable String except) {
        Set<String> open = new HashSet<>(), all = new HashSet<>();
        Set<LocalDate> weeks = new HashSet<>();
        List<FailedLot> failed = new java.util.ArrayList<>();
        for (var cover : production.coveringAiTasks(AiRequestType.WEEKLY_SPEC.taskType(), promotionId)) {
            if (cover.task().id().equals(except)) continue;
            List<String> ids = cover.task().inputs().stream().filter(i -> !isDate(i)).toList();
            if (cover.cover() == ProductionService.Cover.FAILED) {
                if (!ids.isEmpty()) failed.add(new FailedLot(Set.copyOf(ids), cover.at()));
                continue;
            }
            // Los specs antiguos llevaban una fecha: cubren las publicaciones de esa semana (de lunes a domingo)
            cover.task().inputs().stream().filter(WeeklySpecHandler::isDate).forEach(d -> weeks.add(mondayOf(LocalDate.parse(d))));
            all.addAll(ids);
            if (cover.cover() == ProductionService.Cover.OPEN) open.addAll(ids);
        }
        return new Covered(open, all, weeks, failed);
    }

    private List<PublishingQueries.AwaitingContent> pending(String promotionId, Instant now, Covered covered) {
        return publications.awaitingContent(promotionId).stream()
                .filter(p -> p.scheduledAt().isAfter(now) && !covered.covers(p)).toList();
    }

    /** Pendientes de contenido: en producción, sin piezas, con la hora por delante y fuera de cualquier spec abierto o completado. */
    List<PublishingQueries.AwaitingContent> pending(String promotionId, Instant now) {
        return pending(promotionId, now, covered(promotionId, null));
    }

    @Override
    public List<String> autoOrderBatch(String promotionId, Instant now) {
        Covered covered = covered(promotionId, null);
        var pending = pending(promotionId, now, covered);
        if (!due(pending.stream().map(PublishingQueries.AwaitingContent::scheduledAt).toList(), now)) return List.of();
        List<String> ids = pending.stream().map(PublishingQueries.AwaitingContent::id).toList();
        // El mismo lote que acaba de fallar del todo no se repite solo: sería un bucle de encargos que fallan
        Set<String> lot = Set.copyOf(ids);
        boolean failedRecently = covered.failed().stream()
                .anyMatch(f -> f.ids().equals(lot) && f.at().isAfter(now.minus(RETRY_LOT_AFTER)));
        return failedRecently ? List.of() : ids;
    }

    /** Un encargo a mano sin publicaciones indicadas (o solo con fechas, como antes) lleva las pendientes de ahora. */
    @Override
    public List<String> completeInputs(String promotionId, List<String> inputs) {
        if (!inputs.stream().allMatch(WeeklySpecHandler::isDate)) return inputs;
        return pending(promotionId, clock.instant()).stream().map(PublishingQueries.AwaitingContent::id).toList();
    }

    private static boolean isDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("No hay publicaciones pendientes de contenido (en «En producción» y sin piezas) para el spec");
        }
        Set<String> open = covered(promotionId, null).open();
        for (String id : inputs) {
            if (!publications.belongsTo(promotionId, id)) {
                throw new IllegalArgumentException("«" + id + "» no es una publicación de esta promoción");
            }
            if (open.contains(id)) {
                throw new IllegalArgumentException("La publicación «" + id + "» ya está en otro spec pedido o en curso");
            }
        }
    }

    /** Ya no hay nada que preparar: todas las publicaciones del lote se cancelaron (o ya no hay pendientes, en los antiguos). */
    @Override
    public boolean obsolete(AiJob job) {
        return lotPublications(job).isEmpty();
    }

    /** Las publicaciones del lote, como datos para Claude. Un encargo antiguo (con una fecha) toma las pendientes de ahora. */
    private List<Map<String, Object>> lotPublications(AiJob job) {
        if (job.inputs().stream().allMatch(WeeklySpecHandler::isDate)) {
            Covered covered = covered(job.promotionId(), job.taskId());
            return publications.forAi(pending(job.promotionId(), clock.instant(), covered).stream()
                    .map(PublishingQueries.AwaitingContent::id).toList());
        }
        return publications.forAi(job.inputs());
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        var list = lotPublications(job);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("folders", promotions.folderMap(job.promotionId()));
        inputs.put("publications", list);
        inputs.put("expected_outputs", "spec (objeto con el formato de contracts/promo-spec.v1.schema.json) y summary. "
                + "El spec lleva solo las publicaciones de «publications» (cada una con su fecha «scheduled_on» y su hora "
                + "«scheduled_time»), sin depender de ninguna semana. "
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
        int discarded = 0;
        Set<String> lot = job.inputs().stream().filter(i -> !isDate(i)).collect(java.util.stream.Collectors.toSet());
        if (!lot.isEmpty() && spec.path("piezas").isArray()) {
            // Piezas de publicaciones que no son de este lote: se descartan para no duplicar las de otro spec
            ObjectNode filtered = ((ObjectNode) spec).deepCopy();
            ArrayNode keep = json.createArrayNode();
            for (JsonNode piece : spec.path("piezas")) {
                String id = piece.path("publicacion").isString() ? piece.path("publicacion").asString().strip() : "";
                if (id.isEmpty() || lot.contains(id)) keep.add(piece); else discarded++;
            }
            if (discarded > 0) {
                if (keep.isEmpty()) {
                    throw new IllegalArgumentException("Ninguna pieza del spec es de las publicaciones de este encargo");
                }
                filtered.set("piezas", keep);
                spec = filtered;
            }
        }
        String folder = promotions.ordersFolder(job.promotionId())
                .orElseThrow(() -> new IllegalArgumentException("La promoción no tiene carpeta de «Encargos»"));
        Path file;
        try {
            Path dir = Files.createDirectories(Path.of(folder).resolve(SPECS));
            file = fresh(dir, "spec-" + created(job));
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
                + (discarded == 0 ? "" : " · aviso: " + discarded + (discarded == 1 ? " pieza descartada" : " piezas descartadas")
                        + " por no ser de las publicaciones de este encargo")
                + (summary.isEmpty() ? "" : " · " + summary);
    }

    /** El día (hora de España) en que se creó el encargo, para el nombre del archivo. */
    private LocalDate created(AiJob job) {
        Instant at = job.createdAt() == null || job.createdAt().equals(Instant.EPOCH) ? clock.instant() : job.createdAt();
        return at.atZone(java.time.ZoneId.of("Europe/Madrid")).toLocalDate();
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
