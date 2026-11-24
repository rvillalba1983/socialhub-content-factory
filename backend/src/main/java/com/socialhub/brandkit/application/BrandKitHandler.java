package com.socialhub.brandkit.application;

import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.promotion.PromotionQueries;
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
    /** Piezas que se piden si no se indica ninguna: lo común a todas las marcas. La cita se pide aparte (solo El DeLito). */
    static final List<KitPiece> DEFAULT_PIECES = List.of(KitPiece.CIERRE, KitPiece.HISTORIA);

    /** Los datos de entrada del encargo, ya leídos. */
    record Order(List<KitPiece> pieces, String comment, @Nullable Long previousKitId) {}

    private final BrandKits kits;
    private final PromotionQueries promotions;
    private final ObjectMapper json;

    @Override public AiRequestType type() { return AiRequestType.BRAND_KIT; }

    @Override
    public java.util.List<String> completeInputs(java.util.List<String> inputs) {
        boolean hasPieces = inputs.stream().anyMatch(i -> i.startsWith(PIECE) || KitPiece.fromCode(i).isPresent());
        if (hasPieces) return inputs;
        List<String> complete = new ArrayList<>(inputs);
        DEFAULT_PIECES.forEach(p -> complete.add(PIECE + p.code()));
        return complete;
    }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        Order order = parse(inputs);
        if (order.pieces().isEmpty()) throw new IllegalArgumentException("Indica qué piezas quieres en el kit: " + KitPiece.activeCodes() + ".");
        if (order.previousKitId() != null) {
            BrandKit previous = kits.find(order.previousKitId())
                    .orElseThrow(() -> new IllegalArgumentException("El kit anterior (" + order.previousKitId() + ") no existe."));
            if (!previous.brandId().equals(promotions.brandOf(promotionId))) {
                throw new IllegalArgumentException("El kit anterior no es de la marca de esta promoción.");
            }
        }
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
        if (base != null) {
            inputs.put("previous_kit", readKit(base));
        } else {
            kits.approved(job.brandId()).ifPresent(approved -> inputs.put("approved_kit", readKit(approved)));
        }
        inputs.put("expected_outputs", "kit (objeto con el formato de contracts/brand-kit.v1.schema.json: palette, notes y templates, "
                + "con al menos una plantilla por cada pieza de «pieces») y summary. SocialHub valida el kit y no lo usa hasta que "
                + "Raúl lo aprueba. " + (base == null ? "" : "Es otra versión de «previous_kit»: aplica el «comment» de Raúl."));
        return inputs;
    }

    private JsonNode readKit(BrandKit kit) { return json.readTree(kit.kitJson()); }

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
            + "plantilla indica, en unidades de su viewBox, dónde va la ilustración (null si no lleva) y deja esa zona libre o "
            + "semitransparente, sin dibujo opaco encima.";

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
