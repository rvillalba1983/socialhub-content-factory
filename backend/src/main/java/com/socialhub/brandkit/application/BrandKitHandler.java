package com.socialhub.brandkit.application;

import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionQueries;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kit de marca: el encargo cuelga de una promoción de la marca y lleva como datos de entrada las piezas pedidas
 * ({@code pieza:cierre}), el comentario de «Pedir otra versión» ({@code comentario:…}) y el kit anterior
 * ({@code kit-anterior:12}). Claude devuelve el kit en {@code outputs.kit} ({@code contracts/brand-kit.v1.schema.json}); se
 * valida y queda pendiente de la aprobación de Raúl: no se usa hasta entonces.
 */
@Component
@RequiredArgsConstructor
class BrandKitHandler implements AiTypeHandler {

    static final String PIECE = "pieza:";
    static final String COMMENT = "comentario:";
    static final String PREVIOUS = "kit-anterior:";
    /** Piezas que se piden si no se indica ninguna y no se sabe cuáles lleva la marca: lo común a todas (la cita es solo de El DeLito). */
    static final List<KitPiece> DEFAULT_PIECES = List.of(KitPiece.CIERRE, KitPiece.HISTORIA);
    /**
     * Tope del peso del bloque de datos de entrada que añade este manejador (el JSON del encargo sin la identidad de la marca ni
     * el contrato): 512 KB. Si lo pasa por los SVG del kit anterior, esos SVG se omiten (quedan la paleta, los ids y las piezas).
     */
    static final int MAX_INPUTS_BYTES = 512 * 1024;

    /** Los datos de entrada del encargo, ya leídos. */
    record Order(List<KitPiece> pieces, String comment, @Nullable Long previousKitId) {}

    private final BrandKits kits;
    private final PromotionQueries promotions;
    private final ObjectMapper json;

    @Override public AiRequestType type() { return AiRequestType.BRAND_KIT; }

    @Override
    public java.util.List<String> completeInputs(java.util.List<String> inputs) { return complete(inputs, DEFAULT_PIECES); }

    /** Sin piezas indicadas se piden todas las que lleva la marca (también en el encargo genérico de «Nuevo encargo»). */
    @Override
    public java.util.List<String> completeInputs(String promotionId, java.util.List<String> inputs) {
        return complete(inputs, allowedPieces(promotions.brandOf(promotionId)));
    }

    private static List<String> complete(List<String> inputs, List<KitPiece> defaults) {
        boolean hasPieces = inputs.stream().anyMatch(i -> i.startsWith(PIECE) || KitPiece.fromCode(i).isPresent());
        if (hasPieces) return inputs;
        List<String> complete = new ArrayList<>(inputs);
        (defaults.isEmpty() ? DEFAULT_PIECES : defaults).forEach(p -> complete.add(PIECE + p.code()));
        return complete;
    }

    /** Piezas que lleva el kit de la marca (decisiones 13, 50 y 51), en el orden del catálogo. */
    private List<KitPiece> allowedPieces(@Nullable String brandId) {
        if (brandId == null) return List.of();
        List<String> codes = promotions.kitPiecesOf(brandId);
        return codes == null ? List.of() : java.util.Arrays.stream(KitPiece.values()).filter(p -> codes.contains(p.code())).toList();
    }

    private static String codes(List<KitPiece> pieces) { return String.join(", ", pieces.stream().map(KitPiece::code).toList()); }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        Order order = parse(inputs);
        if (order.pieces().isEmpty()) throw new IllegalArgumentException("Indica qué piezas quieres en el kit: " + KitPiece.activeCodes() + ".");
        String brandId = promotions.brandOf(promotionId);
        List<KitPiece> allowed = allowedPieces(brandId);
        if (!allowed.isEmpty()) {
            List<String> notAllowed = order.pieces().stream().filter(p -> !allowed.contains(p)).map(KitPiece::code).toList();
            if (!notAllowed.isEmpty()) {
                throw new IllegalArgumentException("El kit de esta marca no lleva " + String.join(", ", notAllowed)
                        + ". Piezas que lleva: " + codes(allowed) + ".");
            }
        }
        if (order.previousKitId() != null) {
            BrandKit previous = kits.find(order.previousKitId())
                    .orElseThrow(() -> new IllegalArgumentException("El kit anterior (" + order.previousKitId() + ") no existe."));
            if (!previous.brandId().equals(brandId)) {
                throw new IllegalArgumentException("El kit anterior no es de la marca de esta promoción.");
            }
        }
        ensureNoOtherKit(brandId);
    }

    /**
     * Un solo kit en marcha por marca: ni un encargo abierto en cualquiera de sus promociones ni un kit pendiente de aprobar
     * (409). «Pedir otra versión» rechaza antes el pendiente, así que pasa.
     */
    void ensureNoOtherKit(@Nullable String brandId) {
        if (brandId == null) return;
        if (kits.pending(brandId).isPresent()) {
            throw new DomainException("Esta marca ya tiene un kit pendiente de tu aprobación: apruébalo o pide otra versión antes de pedir otro.");
        }
        kits.openOrderPromotion(brandId).ifPresent(promotion -> {
            throw new DomainException("Ya hay un encargo de kit abierto de esta marca (promoción «" + promotion
                    + "»): espera a que Claude lo termine antes de pedir otro.");
        });
    }

    /** Lee los datos de entrada; lo que no entiende es un error (se dice qué formas valen). */
    static Order parse(List<String> inputs) {
        Set<KitPiece> pieces = new LinkedHashSet<>();
        String comment = "";
        @Nullable Long previous = null;
        for (String raw : inputs) {
            String item = raw.strip();
            if (item.isEmpty()) continue;
            if (item.startsWith(COMMENT)) {
                comment = item.substring(COMMENT.length()).strip();
            } else if (item.startsWith(PREVIOUS)) {
                try {
                    previous = Long.parseLong(item.substring(PREVIOUS.length()).strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("«" + item + "» no es válido: después de «kit-anterior:» va el número del kit.");
                }
            } else {
                String code = (item.startsWith(PIECE) ? item.substring(PIECE.length()) : item).strip();
                KitPiece piece = KitPiece.fromCode(code).filter(KitPiece::active).orElseThrow(() -> new IllegalArgumentException(
                        "«" + item + "» no es una pieza que pueda llevar el kit. Piezas válidas: " + KitPiece.activeCodes()
                                + " (con o sin «pieza:» delante). También valen «comentario:…» y «kit-anterior:N»."));
                pieces.add(piece);
            }
        }
        return new Order(List.copyOf(pieces), comment, previous);
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        Order order = parse(job.inputs());
        Map<String, Object> inputs = new LinkedHashMap<>();
        String brandName = promotions.brandRefs().stream().filter(b -> b.id().equals(job.brandId()))
                .map(PromotionQueries.BrandRef::name).findFirst().orElse(job.brandId());
        inputs.put("brand_name", brandName);
        String color = promotions.colorOf(job.brandId());
        if (!color.isBlank()) inputs.put("brand_color", color);
        inputs.put("folders", promotions.folderMap(job.promotionId()));
        String style = promotions.visualStyleOf(job.promotionId());
        if (!style.isBlank()) inputs.put("visual_style", style);
        inputs.put("pieces", order.pieces().stream().map(BrandKitHandler::pieceRules).toList());
        inputs.put("template_rules", TEMPLATE_RULES);
        inputs.put("fonts", FONTS);
        inputs.put("contract", "contracts/brand-kit.v1.schema.json (objeto «outputs.kit»); formato de cada plantilla: docs/plantillas-svg.md");
        inputs.put("image_rule", IMAGE_RULE);
        if (!order.comment().isBlank()) inputs.put("comment", order.comment());
        BrandKit base = order.previousKitId() != null ? kits.find(order.previousKitId()).orElse(null) : null;
        Set<KitPiece> wanted = new LinkedHashSet<>(order.pieces());
        BrandKit reference = base != null ? base : kits.approved(job.brandId()).orElse(null);
        String referenceKey = base != null ? "previous_kit" : "approved_kit";
        if (reference != null) inputs.put(referenceKey, slim(reference, wanted, true));
        if (reference != null && size(inputs) > MAX_INPUTS_BYTES) {
            // Demasiado: se quedan la paleta, los ids y las piezas del kit de referencia, sin sus SVG
            inputs.put(referenceKey, slim(reference, wanted, false));
            inputs.put("reference_note", "Por el tamaño del encargo, «" + referenceKey + "» no lleva los SVG de las plantillas: solo la paleta, "
                    + "los ids y las piezas. Parte de la identidad y de las reglas de este encargo.");
        }
        inputs.put("expected_outputs", "kit (objeto con el formato de contracts/brand-kit.v1.schema.json: palette, notes y templates, "
                + "con al menos una plantilla por cada pieza de «pieces») y summary. SocialHub valida el kit y no lo usa hasta que "
                + "Raúl lo aprueba. " + (base == null ? "" : "Es otra versión de «previous_kit»: aplica el «comment» de Raúl."));
        if (size(inputs) > MAX_INPUTS_BYTES) {
            throw new IllegalStateException("Los datos del encargo del kit pesan más de " + MAX_INPUTS_BYTES / 1024 + " KB.");
        }
        return inputs;
    }

    private int size(Map<String, Object> inputs) { return json.writeValueAsString(inputs).getBytes(StandardCharsets.UTF_8).length; }

    /**
     * El kit de referencia (anterior o aprobado) sin lo que sobra: la paleta, las notas y, por plantilla, id, pieza, nombre y zona
     * de imagen; el SVG solo de las plantillas de las piezas pedidas (y solo si {@code withSvg}).
     */
    private ObjectNode slim(BrandKit kit, Set<KitPiece> wanted, boolean withSvg) {
        ObjectNode node = json.createObjectNode();
        node.put("version", kit.version());
        ArrayNode palette = node.putArray("palette");
        kit.kit().palette().forEach(c -> palette.addObject().put("name", c.name()).put("hex", c.hex()));
        if (kit.kit().notes() != null) node.put("notes", kit.kit().notes());
        ArrayNode templates = node.putArray("templates");
        for (KitTemplate t : kit.kit().templates()) {
            ObjectNode item = templates.addObject();
            item.put("id", t.id()).put("piece", t.piece().code()).put("name", t.name());
            if (t.image() != null) {
                item.putObject("image").put("x", t.image().x()).put("y", t.image().y())
                        .put("width", t.image().width()).put("height", t.image().height());
            }
            if (withSvg && wanted.contains(t.piece())) item.put("svg", t.svg());
        }
        return node;
    }

    /** Lo que Claude debe saber de cada pieza: campos obligatorios y opcionales, proporciones y tamaños de lienzo. */
    private static Map<String, Object> pieceRules(KitPiece piece) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("piece", piece.code());
        rules.put("required_fields", piece.requiredFields().stream().map(f -> "campo-" + f).toList());
        rules.put("optional_fields", piece.optionalFields().stream().map(f -> "campo-" + f).toList());
        rules.put("ratios", piece.ratios().stream().map(r -> r.label() + " (viewBox " + canvas(r) + ")").toList());
        return rules;
    }

    private static String canvas(KitPiece.Ratio ratio) {
        return ratio == KitPiece.Ratio.R4X5 ? "0 0 1080 1350" : "0 0 1080 1920";
    }

    static final List<String> FONTS = List.of("Crimson Text (400, 400 cursiva, 600, 700)", "Fira Sans (400, 400 cursiva, 500, 700)",
            "Bebas Neue (400, todo en mayúsculas)");

    static final String IMAGE_RULE = "La plantilla va ENCIMA de la ilustración de la publicación (no la sustituye): en «image» de cada "
            + "plantilla indica, en unidades de su viewBox, dónde va la ilustración y deja esa zona libre o semitransparente, sin dibujo "
            + "opaco encima. «image» es obligatorio (un objeto x, y, width, height) en cierre e historia; solo la cita puede llevar null.";

    static final List<String> TEMPLATE_RULES = List.of(
            "Cada plantilla es un SVG con el dibujo fijo y campos de texto con nombre: <text id=\"campo-NOMBRE\"> y su caja "
                    + "<rect id=\"caja-NOMBRE\" x y width height>, los dos hijos directos de <svg>. El texto de dentro es el ejemplo de la vista previa.",
            "Raíz <svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1080 1350\"> (4:5) o \"0 0 1080 1920\" (9:16); máximo 64 KB.",
            "Solo se pueden usar: g, defs, rect, circle, ellipse, path, polygon, polyline, line, linearGradient, radialGradient, stop, title y desc. "
                    + "Nada de script, style, image, use, foreignObject, animaciones ni referencias externas, y ningún <text> fuera de los campos.",
            "Fuentes: solo Crimson Text, Fira Sans y Bebas Neue, con los pesos y estilos de «fonts». Cada campo lleva font-family y font-size "
                    + "(tamaño máximo; SocialHub lo baja hasta data-tamano-min si el texto no cabe). Opcionales: font-weight, font-style, fill, "
                    + "fill-opacity, letter-spacing, text-anchor, data-tamano-min, data-lineas-max, data-interlineado, data-vertical, data-fijo.",
            "La caja debe quedar dentro del lienzo y ser lo bastante grande: alto al menos data-interlineado × data-tamano-min.",
            "Solo los campos de «required_fields» y «optional_fields» de cada pieza (más los fijos, con data-fijo=\"true\"); cualquier otro se rechaza.",
            "Los versos y los textos de cada publicación los rellena SocialHub: la plantilla solo lleva textos de ejemplo.");

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode kit = outputs.path("kit");
        if (!kit.isObject()) throw new IllegalArgumentException("Falta el kit («outputs.kit»)");
        Order order = parse(job.inputs());
        Set<String> present = new LinkedHashSet<>();
        for (JsonNode t : kit.path("templates")) if (t.path("piece").isString()) present.add(t.path("piece").asString());
        List<KitPiece> allowed = allowedPieces(job.brandId());
        Set<KitPiece> extra = new LinkedHashSet<>();
        for (JsonNode t : kit.path("templates")) {
            if (t.path("piece").isString()) KitPiece.fromCode(t.path("piece").asString()).ifPresent(p -> {
                if (!order.pieces().contains(p)) extra.add(p);
            });
        }
        if (!extra.isEmpty()) {
            boolean notAllowed = !allowed.isEmpty() && extra.stream().anyMatch(p -> !allowed.contains(p));
            throw new IllegalArgumentException("El kit trae plantillas de piezas que no se pidieron: " + codes(List.copyOf(extra))
                    + ". Piezas pedidas: " + codes(order.pieces()) + (notAllowed ? " (y la marca solo lleva " + codes(allowed) + ")" : "") + ".");
        }
        List<String> missing = order.pieces().stream().map(KitPiece::code).filter(c -> !present.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Faltan plantillas de las piezas pedidas: " + String.join(", ", missing) + ".");
        }
        String kitJson = json.writeValueAsString(kit);
        // Se comprueba antes de guardar: un fallo dentro del servicio dejaría la transacción del resultado marcada para deshacerse
        List<String> errors = kits.check(kitJson);
        if (!errors.isEmpty()) throw new IllegalArgumentException("El kit no es válido: " + String.join(" ", errors));
        BrandKit saved = kits.propose(job.brandId(), kitJson, job.taskId());
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return "Kit de marca (versión " + saved.version() + ") pendiente de tu aprobación: " + saved.kit().templates().size()
                + (saved.kit().templates().size() == 1 ? " plantilla" : " plantillas") + ". No se usa hasta que lo apruebes en la ficha de la marca"
                + (summary.isEmpty() ? "" : " · " + summary);
    }
}
