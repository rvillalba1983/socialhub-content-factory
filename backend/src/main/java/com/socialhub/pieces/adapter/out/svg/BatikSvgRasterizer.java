package com.socialhub.pieces.adapter.out.svg;

import com.socialhub.pieces.domain.SvgRasterizer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.batik.bridge.NoLoadExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadScriptSecurity;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Pinta con Apache Batik los fondos SVG de Claude. Antes de pintar, un validador estricto con lista de permitidos
 * (elementos y atributos de dibujo) rechaza todo lo demás: script, foreignObject, image, use, style, entidades,
 * DOCTYPE y cualquier referencia que no sea interna ({@code #id} o {@code url(#id)}). Además, Batik arranca sin
 * permiso para cargar recursos externos ni ejecutar scripts.
 */
@Component
public class BatikSvgRasterizer implements SvgRasterizer {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final int MAX_ELEMENTS = 2000;
    private static final int MAX_DEPTH = 24;
    private static final int MAX_PIXELS = 1080 * 1920;

    private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "rect", "circle", "ellipse", "path", "polygon",
            "polyline", "line", "linearGradient", "radialGradient", "stop", "text", "tspan", "title", "desc");

    private static final Set<String> ATTRIBUTES = Set.of("id", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
            "fx", "fy", "width", "height", "d", "points", "transform", "fill", "fill-opacity", "fill-rule", "stroke",
            "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
            "stroke-dashoffset", "opacity", "offset", "stop-color", "stop-opacity", "gradientUnits", "gradientTransform",
            "spreadMethod", "viewBox", "preserveAspectRatio", "version", "font-family", "font-size", "font-weight",
            "font-style", "text-anchor", "dx", "dy", "letter-spacing", "baseline-shift", "dominant-baseline", "href");

    /** Lo único que puede ir dentro de {@code url(...)}: una referencia interna. */
    private static final Pattern URL_REF = Pattern.compile("url\\(\\s*['\"]?#[\\w.-]+['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_URL = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "javascript:|data:|https?:|file:|ftp:|@import|expression\\s*\\(|<|&|\\\\", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isSafe(String svg) {
        return parse(svg).isPresent();
    }

    @Override
    public Optional<BufferedImage> rasterize(String svg, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return Optional.empty();
        Optional<Document> parsed = parse(svg);
        if (parsed.isEmpty()) return Optional.empty();
        try {
            Element root = parsed.get().getDocumentElement();
            if (!root.hasAttribute("viewBox")) root.setAttribute("viewBox", "0 0 " + width + " " + height);
            root.setAttribute("preserveAspectRatio", "xMidYMid slice");
            root.removeAttribute("width");
            root.removeAttribute("height");
            Capture capture = new Capture();
            capture.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) width);
            capture.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) height);
            capture.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
            capture.addTranscodingHint(ImageTranscoder.KEY_EXECUTE_ONLOAD, false);
            capture.addTranscodingHint(ImageTranscoder.KEY_ALLOWED_SCRIPT_TYPES, "");
            capture.addTranscodingHint(ImageTranscoder.KEY_XML_PARSER_VALIDATING, false);
            capture.transcode(new TranscoderInput(new StringReader(serialize(parsed.get()))), new TranscoderOutput());
            BufferedImage image = capture.image;
            if (image == null) return Optional.empty();
            BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var g = rgb.createGraphics();
            g.drawImage(image, 0, 0, Color.WHITE, null);
            g.dispose();
            return Optional.of(rgb);
        } catch (TranscoderException | RuntimeException | StackOverflowError | javax.xml.transform.TransformerException e) {
            return Optional.empty();
        }
    }

    /** Recoge la imagen en memoria en lugar de escribir un archivo, y no deja a Batik cargar nada de fuera. */
    private static final class Capture extends ImageTranscoder {
        BufferedImage image;

        @Override
        public BufferedImage createImage(int w, int h) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) {
            this.image = img;
        }

        @Override
        protected UserAgent createUserAgent() {
            return new SVGAbstractTranscoderUserAgent() {
                @Override
                public org.apache.batik.bridge.ExternalResourceSecurity getExternalResourceSecurity(
                        org.apache.batik.util.ParsedURL resourceURL, org.apache.batik.util.ParsedURL docURL) {
                    return new NoLoadExternalResourceSecurity();
                }

                @Override
                public org.apache.batik.bridge.ScriptSecurity getScriptSecurity(String scriptType,
                        org.apache.batik.util.ParsedURL scriptURL, org.apache.batik.util.ParsedURL docURL) {
                    return new NoLoadScriptSecurity(scriptType);
                }
            };
        }
    }

    // ---------------------------------------------------------------- validación

    private Optional<Document> parse(String svg) {
        if (svg == null || svg.isBlank() || svg.length() > MAX_CHARS) return Optional.empty();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));
            Document document = builder.parse(new InputSource(new StringReader(svg.strip())));
            Element root = document.getDocumentElement();
            if (!"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) return Optional.empty();
            int[] count = {0};
            return check(root, 0, count) ? Optional.of(document) : Optional.empty();
        } catch (Exception | StackOverflowError e) {
            return Optional.empty();
        }
    }

    private static boolean check(Element element, int depth, int[] count) {
        if (depth > MAX_DEPTH || ++count[0] > MAX_ELEMENTS) return false;
        if (!SVG_NS.equals(element.getNamespaceURI()) || !ELEMENTS.contains(element.getLocalName())) return false;
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName() == null ? attr.getNodeName() : attr.getLocalName();
            String value = attr.getNodeValue();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns)) {
                // Solo se admite declarar el espacio de nombres del SVG (y xlink, para href)
                if (!SVG_NS.equals(value) && !XLINK_NS.equals(value)) return false;
                continue;
            }
            if (ns != null && !XLINK_NS.equals(ns)) return false;
            if (ns != null && !"href".equals(name)) return false;
            if (!ATTRIBUTES.contains(name)) return false;
            if (!safeValue(name, value)) return false;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> { if (!check((Element) child, depth + 1, count)) return false; }
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    if (FORBIDDEN.matcher(child.getNodeValue()).find()) return false;
                }
                case Node.COMMENT_NODE -> { }
                default -> { return false; } // entidades, instrucciones de proceso…
            }
        }
        return true;
    }

    private static boolean safeValue(String name, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if ("href".equals(name)) return value.matches("#[\\w.-]+");
        if (FORBIDDEN.matcher(lower).find()) return false;
        if (ANY_URL.matcher(value).find()) return URL_REF.matcher(value).replaceAll("").indexOf("url") < 0;
        return true;
    }

    private static String serialize(Document document) throws javax.xml.transform.TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter out = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toString();
    }
}
