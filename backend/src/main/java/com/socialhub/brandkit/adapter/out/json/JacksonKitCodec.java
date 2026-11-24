package com.socialhub.brandkit.adapter.out.json;

import com.socialhub.brandkit.domain.ImageArea;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.brandkit.domain.PaletteColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lee y escribe el JSON del kit ({@code contracts/brand-kit.v1.schema.json}). Comprueba la forma (tipos, campos y
 * propiedades desconocidas) con mensajes en español; las reglas del contenido son de {@code KitValidator}.
 */
@Component
class JacksonKitCodec implements KitCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> KIT_KEYS = Set.of("palette", "notes", "templates");
    private static final Set<String> COLOR_KEYS = Set.of("name", "hex");
    private static final Set<String> TEMPLATE_KEYS = Set.of("id", "piece", "name", "svg", "image");
    private static final Set<String> IMAGE_KEYS = Set.of("x", "y", "width", "height");

    @Override
    public KitContent read(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new InvalidKitException(List.of("El kit no es un JSON válido."));
        }
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) throw new InvalidKitException(List.of("El kit debe ser un objeto JSON con «palette» y «templates»."));
        unknown(root, KIT_KEYS, "el kit", errors);
        List<PaletteColor> palette = new ArrayList<>();
        for (JsonNode item : array(root, "palette", errors)) {
            if (!item.isObject()) { errors.add("Cada color de «palette» debe ser un objeto {name, hex}."); continue; }
            unknown(item, COLOR_KEYS, "un color de la paleta", errors);
            palette.add(new PaletteColor(text(item, "name", "un color de la paleta", errors), text(item, "hex", "un color de la paleta", errors)));
        }
        JsonNode notesNode = root.get("notes");
        String notes = null;
        if (notesNode != null && !notesNode.isNull()) {
            if (notesNode.isString()) notes = notesNode.asString();
            else errors.add("«notes» debe ser un texto.");
        }
        List<KitTemplate> templates = new ArrayList<>();
        for (JsonNode item : array(root, "templates", errors)) {
            if (!item.isObject()) { errors.add("Cada plantilla de «templates» debe ser un objeto."); continue; }
            KitTemplate template = template(item, errors);
            if (template != null) templates.add(template);
        }
        if (!errors.isEmpty()) throw new InvalidKitException(errors);
        return new KitContent(palette, notes, templates);
    }

    private static @Nullable KitTemplate template(JsonNode node, List<String> errors) {
        String id = text(node, "id", "una plantilla", errors);
        String label = "La plantilla «" + id + "»";
        unknown(node, TEMPLATE_KEYS, "la plantilla «" + id + "»", errors);
        String pieceCode = text(node, "piece", label, errors);
        KitPiece piece = KitPiece.fromCode(pieceCode).orElse(null);
        if (piece == null && !pieceCode.isEmpty()) {
            errors.add(label + " tiene una pieza desconocida («" + pieceCode + "»): usa cita, cierre, historia (infografia y meme aún no se activan).");
        }
        String name = text(node, "name", label, errors);
        String svg = text(node, "svg", label, errors);
        ImageArea image = image(node.get("image"), label, errors);
        return piece == null ? null : new KitTemplate(id, piece, name, svg, image);
    }

    private static @Nullable ImageArea image(@Nullable JsonNode node, String label, List<String> errors) {
        if (node == null || node.isNull()) return null;
        if (!node.isObject()) { errors.add(label + ": «image» debe ser null o un objeto {x, y, width, height}."); return null; }
        unknown(node, IMAGE_KEYS, "la zona de imagen de " + label, errors);
        double[] values = new double[4];
        String[] names = {"x", "y", "width", "height"};
        for (int i = 0; i < 4; i++) {
            JsonNode value = node.get(names[i]);
            if (value == null || !value.isNumber()) {
                errors.add(label + ": en «image», «" + names[i] + "» debe ser un número.");
                return null;
            }
            values[i] = value.asDouble();
        }
        return new ImageArea(values[0], values[1], values[2], values[3]);
    }

    private static Iterable<JsonNode> array(JsonNode root, String key, List<String> errors) {
        JsonNode node = root.get(key);
        if (node == null || !node.isArray()) {
            errors.add("Falta «" + key + "» (una lista).");
            return List.of();
        }
        return node;
    }

    private static String text(JsonNode node, String key, String what, List<String> errors) {
        JsonNode value = node.get(key);
        if (value == null || !value.isString()) {
            errors.add("Falta «" + key + "» (un texto) en " + what + ".");
            return "";
        }
        return value.asString();
    }

    private static void unknown(JsonNode node, Set<String> allowed, String what, List<String> errors) {
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) errors.add("Propiedad desconocida «" + name + "» en " + what + ".");
        }
    }

    /** El kit como JSON normalizado (sin propiedades desconocidas), tal como se guarda. */
    @Override
    public String write(KitContent kit) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode palette = root.putArray("palette");
        for (PaletteColor color : kit.palette()) palette.addObject().put("name", color.name()).put("hex", color.hex());
        if (kit.notes() == null) root.putNull("notes"); else root.put("notes", kit.notes());
        ArrayNode templates = root.putArray("templates");
        for (KitTemplate template : kit.templates()) {
            ObjectNode node = templates.addObject();
            node.put("id", template.id()).put("piece", template.piece().code()).put("name", template.name()).put("svg", template.svg());
            ImageArea image = template.image();
            if (image == null) node.putNull("image");
            else node.putObject("image").put("x", image.x()).put("y", image.y()).put("width", image.width()).put("height", image.height());
        }
        return root.toString();
    }
}
