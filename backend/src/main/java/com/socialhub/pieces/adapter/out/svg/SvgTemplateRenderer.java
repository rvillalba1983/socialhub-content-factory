package com.socialhub.pieces.adapter.out.svg;

import com.socialhub.pieces.domain.SvgRasterizer;
import com.socialhub.pieces.domain.TemplateCheck;
import com.socialhub.pieces.domain.TemplateField;
import com.socialhub.pieces.domain.TemplateRender;
import com.socialhub.pieces.domain.TemplateRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Motor de plantillas SVG (formato en {@code docs/plantillas-svg.md}). Batik pinta el dibujo de la plantilla sin sus
 * {@code <text>} ni sus {@code <rect id="caja-…">}; después el texto se escribe encima con Java 2D ({@code TextLayout})
 * y las fuentes incluidas. Así los textos nunca pasan por el SVG y el validador estricto no se relaja.
 */
@Component
public class SvgTemplateRenderer implements TemplateRenderer {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)");
    private static final Pattern COLOR = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    private static final Pattern WEIGHT = Pattern.compile("normal|bold|[1-9]00");
    private static final double RATIO_TOLERANCE = 0.01;

    private final SvgRasterizer rasterizer;

    public SvgTemplateRenderer(SvgRasterizer rasterizer) {
        this.rasterizer = rasterizer;
    }

    /** Un campo ya validado, con todos sus atributos de texto. */
    private record Spec(TemplateField field, Font font, Color color, double letterSpacing, String anchor, int maxSize,
            int minSize, int maxLines, double lineHeight, String vertical) {}

    private record Analysis(double minX, double minY, double vbWidth, double vbHeight, List<Spec> specs, List<String> errors) {
        TemplateCheck toCheck() {
            return new TemplateCheck((int) Math.round(vbWidth), (int) Math.round(vbHeight),
                    specs.stream().map(Spec::field).toList(), errors);
        }
    }

    // ---------------------------------------------------------------- API

    @Override
    public TemplateCheck check(String templateSvg) {
        try {
            return analyze(templateSvg).toCheck();
        } catch (RuntimeException | StackOverflowError e) {
            return new TemplateCheck(0, 0, List.of(), List.of("No se ha podido revisar la plantilla: " + e));
        }
    }

    @Override
    public TemplateRender preview(String templateSvg, int width, int height) {
        return paint(templateSvg, null, width, height);
    }

    @Override
    public TemplateRender render(String templateSvg, Map<String, String> texts, int width, int height) {
        return paint(templateSvg, texts, width, height);
    }

    private TemplateRender paint(String templateSvg, @org.jspecify.annotations.Nullable Map<String, String> texts, int width, int height) {
        try {
            Analysis analysis = analyze(templateSvg);
            if (!analysis.errors().isEmpty()) return new TemplateRender(null, List.of(), analysis.errors());
            double templateRatio = analysis.vbWidth() / analysis.vbHeight();
            if (width <= 0 || height <= 0 || Math.abs((double) width / height - templateRatio) / templateRatio > RATIO_TOLERANCE) {
                return error("La proporción pedida (" + width + "×" + height + ") no coincide con la de la plantilla ("
                        + trim(analysis.vbWidth()) + "×" + trim(analysis.vbHeight()) + ").");
            }
            Optional<BufferedImage> background = rasterizer.rasterize(drawing(templateSvg), width, height);
            if (background.isEmpty()) {
                return error("No se ha podido pintar el dibujo de la plantilla (comprueba el tamaño pedido: como máximo 1080×1920 píxeles).");
            }
            BufferedImage image = background.get();
            List<String> warnings = new ArrayList<>();
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.scale(width / analysis.vbWidth(), height / analysis.vbHeight());
                g.translate(-analysis.minX(), -analysis.minY());
                for (Spec spec : analysis.specs()) {
                    String text = texts == null || spec.field().fixed() ? spec.field().example()
                            : texts.getOrDefault(spec.field().name(), "");
                    draw(g, spec, text == null ? "" : text, warnings);
                }
            } finally {
                g.dispose();
            }
            if (texts != null) {
                Set<String> names = new HashSet<>();
                analysis.specs().forEach(s -> names.add(s.field().name()));
                for (String key : new LinkedHashSet<>(texts.keySet())) {
                    if (!names.contains(key)) {
                        warnings.add("Se ha enviado un texto para «" + key + "», pero la plantilla no tiene ese campo; se ignora.");
                    }
                }
            }
            return new TemplateRender(image, warnings, List.of());
        } catch (Exception | StackOverflowError e) {
            return error("Error inesperado al pintar la plantilla: " + e);
        }
    }

    private static TemplateRender error(String message) {
        return new TemplateRender(null, List.of(), List.of(message));
    }

    // ---------------------------------------------------------------- dibujo del texto

    private void draw(Graphics2D g, Spec spec, String rawText, List<String> warnings) {
        TemplateField field = spec.field();
        Font base = spec.font();
        StringBuilder clean = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        normalized.codePoints().forEach(cp -> {
            if (cp == '\n' || base.canDisplay(cp)) clean.appendCodePoint(cp);
            else missing.add(new String(Character.toChars(cp)));
        });
        if (!missing.isEmpty()) {
            warnings.add("El campo «" + field.name() + "» tiene caracteres sin glifo en su fuente (se han quitado): "
                    + String.join(" ", missing.stream().limit(8).toList()) + (missing.size() > 8 ? " …" : "") + ".");
        }
        TextFitter.Result result = TextFitter.fit(clean.toString(), base, field.width(), field.height(), spec.maxSize(),
                spec.minSize(), spec.lineHeight(), spec.maxLines(), spec.letterSpacing());
        if (result.truncated()) {
            warnings.add("El texto del campo «" + field.name() + "» no cabe en su caja ni al tamaño mínimo (" + spec.minSize()
                    + "); se ha recortado con «…».");
        }
        if (result.lines().isEmpty()) return;
        Font font = TextFitter.derive(base, result.size(), spec.letterSpacing());
        LineMetrics metrics = font.getLineMetrics("Hg", TextFitter.FRC);
        double lineBox = spec.lineHeight() * result.size();
        double block = lineBox * result.lines().size();
        double top = switch (spec.vertical()) {
            case "centro" -> field.y() + (field.height() - block) / 2;
            case "abajo" -> field.y() + field.height() - block;
            default -> field.y();
        };
        g.setColor(spec.color());
        for (int i = 0; i < result.lines().size(); i++) {
            String line = result.lines().get(i);
            if (line.isEmpty()) continue;
            double w = TextFitter.width(line, font);
            double x = switch (spec.anchor()) {
                case "middle" -> field.x() + (field.width() - w) / 2;
                case "end" -> field.x() + field.width() - w;
                default -> field.x();
            };
            double baseline = top + i * lineBox + (lineBox - (metrics.getAscent() + metrics.getDescent())) / 2 + metrics.getAscent();
            new TextLayout(line, font, TextFitter.FRC).draw(g, (float) x, (float) baseline);
        }
    }

    // ---------------------------------------------------------------- validación

    private Analysis analyze(String svg) {
        List<String> errors = new ArrayList<>();
        Analysis failed = new Analysis(0, 0, 0, 0, List.of(), errors);
        if (svg == null || svg.isBlank()) {
            errors.add("La plantilla está vacía.");
            return failed;
        }
        if (svg.length() > SvgRasterizer.MAX_CHARS) {
            errors.add("La plantilla supera el máximo de " + SvgRasterizer.MAX_CHARS / 1024 + " KB.");
            return failed;
        }
        Document document;
        try {
            document = SecureXml.parse(svg);
        } catch (Exception e) {
            errors.add("La plantilla no es un XML válido (tampoco se admiten DOCTYPE ni entidades).");
            return failed;
        }
        Element root = document.getDocumentElement();
        if (!"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) {
            errors.add("La raíz debe ser <svg xmlns=\"" + SVG_NS + "\">.");
            return failed;
        }
        double[] vb = viewBox(root, errors);
        List<Spec> specs = new ArrayList<>();
        collectFields(root, specs, errors);
        if (!drawingIsSafe(svg)) {
            errors.add("El dibujo de la plantilla usa elementos o atributos no permitidos (solo formas, degradados, "
                    + "título y descripción; nada de script, style, image, use ni referencias externas).");
        }
        return new Analysis(vb[0], vb[1], vb[2], vb[3], specs, errors);
    }

    private boolean drawingIsSafe(String svg) {
        try {
            return rasterizer.isSafe(drawing(svg));
        } catch (Exception e) {
            return false;
        }
    }

    /** El SVG de la plantilla sin sus textos ni sus cajas: solo el dibujo, que es lo que pinta Batik. */
    private static String drawing(String svg) throws Exception {
        Document document = SecureXml.parse(svg);
        Element root = document.getDocumentElement();
        List<Element> toRemove = new ArrayList<>(elements(root, "text"));
        for (Element rect : elements(root, "rect")) {
            if (rect.getAttribute("id").startsWith("caja-")) toRemove.add(rect);
        }
        for (Element element : toRemove) {
            if (element.getParentNode() != null) element.getParentNode().removeChild(element);
        }
        return SecureXml.serialize(document);
    }

    private static double[] viewBox(Element root, List<String> errors) {
        double[] none = {0, 0, 0, 0};
        if (!root.hasAttribute("viewBox")) {
            errors.add("Falta el atributo viewBox en <svg> (por ejemplo viewBox=\"0 0 1080 1350\").");
            return none;
        }
        String[] parts = root.getAttribute("viewBox").strip().split("[\\s,]+");
        if (parts.length != 4 || !java.util.Arrays.stream(parts).allMatch(p -> NUMBER.matcher(p).matches())) {
            errors.add("El viewBox debe tener 4 números (por ejemplo \"0 0 1080 1350\").");
            return none;
        }
        double[] vb = java.util.Arrays.stream(parts).mapToDouble(Double::parseDouble).toArray();
        if (vb[2] < 100 || vb[2] > 4000 || vb[3] < 100 || vb[3] > 4000) {
            errors.add("El ancho y el alto del viewBox deben estar entre 100 y 4000.");
            return none;
        }
        return vb;
    }

    private static void collectFields(Element root, List<Spec> specs, List<String> errors) {
        Map<String, Element> boxes = new LinkedHashMap<>();
        for (Element rect : elements(root, "rect")) {
            String id = rect.getAttribute("id");
            if (!id.startsWith("caja-")) continue;
            String name = id.substring(5);
            if (rect.getParentNode() != root) {
                errors.add("La caja «" + id + "» debe ser hija directa de <svg>.");
            } else if (boxes.containsKey(name)) {
                errors.add("La caja «" + id + "» está repetida.");
            } else {
                boxes.put(name, rect);
            }
        }
        Set<String> seen = new HashSet<>();
        Set<String> usedBoxes = new HashSet<>();
        for (Element text : elements(root, "text")) {
            String id = text.getAttribute("id");
            if (text.getParentNode() != root) {
                errors.add("El <text>" + (id.isEmpty() ? "" : " «" + id + "»")
                        + " está dentro de otro elemento; los textos deben ser hijos directos de <svg>.");
                continue;
            }
            if (!id.startsWith("campo-")) {
                errors.add("Hay un <text> sin id «campo-nombre»" + (id.isEmpty() ? "" : " (tiene «" + id + "»)")
                        + ": todo texto debe ser un campo; un texto fijo es un campo con data-fijo=\"true\".");
                continue;
            }
            String name = id.substring(6);
            if (!NAME.matcher(name).matches()) {
                errors.add("El id «" + id + "» no es válido: el nombre lleva minúsculas, cifras y _, empieza por letra y tiene hasta 32 caracteres.");
                continue;
            }
            if (!seen.add(name)) {
                errors.add("El campo «" + id + "» está repetido.");
                continue;
            }
            boolean hasChildElements = false;
            for (Node c = text.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNodeType() == Node.ELEMENT_NODE) hasChildElements = true;
            }
            if (hasChildElements) {
                errors.add("El campo «" + id + "» tiene elementos dentro (por ejemplo <tspan>); solo puede llevar texto.");
            }
            Element box = boxes.get(name);
            if (box == null) {
                errors.add("El campo «" + id + "» no tiene su caja <rect id=\"caja-" + name + "\">.");
            } else {
                usedBoxes.add(name);
            }
            int before = errors.size();
            double[] rect = box == null ? null : boxGeometry(box, errors);
            Spec spec = fieldSpec(name, text, rect, errors);
            if (errors.size() == before && spec != null && !hasChildElements) specs.add(spec);
        }
        for (String name : boxes.keySet()) {
            if (!usedBoxes.contains(name) && !seen.contains(name)) {
                errors.add("La caja «caja-" + name + "» no tiene su campo <text id=\"campo-" + name + "\">.");
            }
        }
    }

    private static double[] boxGeometry(Element box, List<String> errors) {
        String id = box.getAttribute("id");
        double[] values = new double[4];
        String[] attrs = {"x", "y", "width", "height"};
        for (int i = 0; i < 4; i++) {
            String value = box.getAttribute(attrs[i]).strip();
            if (!NUMBER.matcher(value).matches()) {
                errors.add("La caja «" + id + "» necesita x, y, width y height numéricos y sin unidades.");
                return new double[] {0, 0, 0, 0};
            }
            values[i] = Double.parseDouble(value);
        }
        if (values[2] <= 0 || values[3] <= 0) {
            errors.add("La caja «" + id + "» debe tener width y height mayores que 0.");
        }
        return values;
    }

    private static Spec fieldSpec(String name, Element text, double[] box, List<String> errors) {
        String label = "El campo «campo-" + name + "» ";
        int before = errors.size();
        String family = text.getAttribute("font-family").strip();
        Font font = null;
        if (family.isEmpty()) {
            errors.add(label + "necesita font-family (" + String.join(", ", BundledFonts.families()) + ").");
        } else {
            String weight = attr(text, "font-weight", "normal");
            String style = attr(text, "font-style", "normal");
            if (!WEIGHT.matcher(weight).matches()) errors.add(label + "tiene un font-weight no válido («" + weight + "»): usa normal, bold o 100 a 900.");
            if (!style.equals("normal") && !style.equals("italic")) errors.add(label + "tiene un font-style no válido («" + style + "»): usa normal o italic.");
            font = BundledFonts.resolve(family, weight, style).orElse(null);
            if (font == null) {
                errors.add(label + "usa la fuente «" + family + "», que no está incluida. Fuentes disponibles: "
                        + String.join(", ", BundledFonts.families()) + ".");
            }
        }
        Double size = number(text, "font-size", null, 8, 400, label, "font-size (8 a 400)", errors);
        if (size == null && !text.hasAttribute("font-size")) errors.add(label + "necesita font-size (8 a 400).");
        String fill = attr(text, "fill", "#000000");
        if (!COLOR.matcher(fill).matches()) errors.add(label + "tiene un fill no válido («" + fill + "»): usa #RGB o #RRGGBB.");
        Double opacity = number(text, "fill-opacity", 1.0, 0, 1, label, "fill-opacity (0 a 1)", errors);
        Double spacing = number(text, "letter-spacing", 0.0, -5, 40, label, "letter-spacing (-5 a 40)", errors);
        String anchor = attr(text, "text-anchor", "start");
        if (!Set.of("start", "middle", "end").contains(anchor)) errors.add(label + "tiene un text-anchor no válido («" + anchor + "»): start, middle o end.");
        String vertical = attr(text, "data-vertical", "arriba");
        if (!Set.of("arriba", "centro", "abajo").contains(vertical)) errors.add(label + "tiene un data-vertical no válido («" + vertical + "»): arriba, centro o abajo.");
        Double lineHeight = number(text, "data-interlineado", 1.2, 0.8, 2.5, label, "data-interlineado (0,8 a 2,5)", errors);
        Double lines = number(text, "data-lineas-max", 30.0, 1, 30, label, "data-lineas-max (1 a 30)", errors);
        if (lines != null && lines != Math.floor(lines)) errors.add(label + "necesita un data-lineas-max entero (1 a 30).");
        String fixedAttr = attr(text, "data-fijo", "false");
        if (!fixedAttr.equals("true") && !fixedAttr.equals("false")) errors.add(label + "tiene un data-fijo no válido («" + fixedAttr + "»): true o false.");
        Double min = null;
        if (size != null) {
            double defaultMin = Math.min(size, Math.max(12, size / 2));
            min = number(text, "data-tamano-min", defaultMin, 8, size, label, "data-tamano-min (8 hasta el font-size)", errors);
        }
        if (errors.size() != before || font == null || size == null || box == null || min == null || opacity == null
                || spacing == null || lineHeight == null || lines == null) return null;
        String example = text.getTextContent().strip();
        TemplateField field = new TemplateField(name, box[0], box[1], box[2], box[3], example, fixedAttr.equals("true"));
        Color color = Color.decode(fill.length() == 4
                ? "#" + fill.charAt(1) + fill.charAt(1) + fill.charAt(2) + fill.charAt(2) + fill.charAt(3) + fill.charAt(3) : fill);
        Color withAlpha = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(opacity * 255));
        return new Spec(field, font, withAlpha, spacing, anchor, (int) Math.round(size), (int) Math.round(min),
                (int) Math.round(lines), lineHeight, vertical);
    }

    private static String attr(Element element, String name, String fallback) {
        return element.hasAttribute(name) ? element.getAttribute(name).strip() : fallback;
    }

    /** Lee un atributo numérico en un rango; si está mal escrito o fuera de rango, anota el error y devuelve nulo. */
    private static Double number(Element element, String name, Double fallback, double min, double max, String label,
            String description, List<String> errors) {
        if (!element.hasAttribute(name)) return fallback;
        String value = element.getAttribute(name).strip();
        if (!NUMBER.matcher(value).matches()) {
            errors.add(label + "tiene un " + description + " no válido («" + value + "»): debe ser un número sin unidades.");
            return null;
        }
        double number = Double.parseDouble(value);
        if (number < min || number > max) {
            errors.add(label + "tiene un " + description + " fuera de rango («" + value + "»).");
            return null;
        }
        return number;
    }

    private static List<Element> elements(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = root.getElementsByTagNameNS(SVG_NS, localName);
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
