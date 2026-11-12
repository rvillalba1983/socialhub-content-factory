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

/**
 * spec.json por lotes: Claude devuelve el spec en {@code outputs.spec} (contrato promo-spec.v1). SocialHub lo
 * comprueba, lo guarda en {@code <Encargos de la promoción>/specs-de-claude} y encarga él mismo las piezas. Esa
 * subcarpeta no la vigila nadie, así que el spec no se encarga dos veces.
 * <p>
 * El lote son las publicaciones pendientes de contenido de la promoción: «En producción», sin piezas (archivos), con la
 * hora de publicación por delante y que no estén ya en un spec abierto o completado. Se pide en cuanto hay 3 o más, o
 * cuando la más próxima sale en menos de 3 días (aunque sean 1 o 2). El encargo lleva los identificadores de esas
 * publicaciones como datos de entrada, así que cada publicación está en un solo spec; si el spec falla del todo
 * (sin reintentos) o se cancela, sus publicaciones vuelven a contar como pendientes.
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

    /** Publicaciones de la promoción en un spec abierto, y en uno abierto o ya completado. */
    private record Covered(Set<String> open, Set<String> all) {}

    private Covered covered() {
        Set<String> open = new HashSet<>(), all = new HashSet<>();
        for (var cover : production.coveringAiTasks(AiRequestType.WEEKLY_SPEC.taskType())) {
            all.addAll(cover.task().inputs());
            if (cover.open()) open.addAll(cover.task().inputs());
        }
        return new Covered(open, all);
    }

    /** Pendientes de contenido: en producción, sin piezas, con la hora por delante y fuera de cualquier spec abierto o completado. */
    List<PublishingQueries.AwaitingContent> pending(String promotionId, Instant now) {
        Set<String> covered = covered().all();
        return publications.awaitingContent(promotionId).stream()
                .filter(p -> p.scheduledAt().isAfter(now) && !covered.contains(p.id())).toList();
    }

    @Override
    public List<String> autoOrderBatch(String promotionId, Instant now) {
        var pending = pending(promotionId, now);
        return due(pending.stream().map(PublishingQueries.AwaitingContent::scheduledAt).toList(), now)
                ? pending.stream().map(PublishingQueries.AwaitingContent::id).toList() : List.of();
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
        Set<String> open = covered().open();
        for (String id : inputs) {
            if (!publications.belongsTo(promotionId, id)) {
                throw new IllegalArgumentException("«" + id + "» no es una publicación de esta promoción");
            }
            if (open.contains(id)) {
                throw new IllegalArgumentException("La publicación «" + id + "» ya está en otro spec pedido o en curso");
            }
        }
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        var list = publications.forAi(job.inputs());
        if (list.isEmpty()) {
            // Un encargo anterior a los lotes, con una fecha como dato: se piden las pendientes de ahora
            list = publications.forAi(publications.awaitingContent(job.promotionId()).stream().map(PublishingQueries.AwaitingContent::id).toList());
        }
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
