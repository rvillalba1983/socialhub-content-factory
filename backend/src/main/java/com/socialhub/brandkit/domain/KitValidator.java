package com.socialhub.brandkit.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Revisa un kit entero y devuelve todos los errores (en español, porque los lee Raúl). Cada plantilla pasa por el motor de
 * plantillas ({@link TemplateChecker}) y además debe tener los campos de su pieza y la proporción que le toca.
 */
public class KitValidator {

    public static final int MAX_NOTES = 1000;
    public static final int MAX_SVG_BYTES = 64 * 1024;
    static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    static final Pattern VIEWBOX_ORIGIN = Pattern.compile(
            "viewBox\\s*=\\s*[\"']\\s*(-?[0-9.eE+-]+)[\\s,]+(-?[0-9.eE+-]+)[\\s,]+[0-9.eE+-]+[\\s,]+[0-9.eE+-]+\\s*[\"']");
    static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final TemplateChecker checker;

    public KitValidator(TemplateChecker checker) { this.checker = checker; }

    public List<String> validate(KitContent kit) {
        List<String> errors = new ArrayList<>();
        palette(kit, errors);
        String notes = kit.notes();
        if (notes != null && notes.length() > MAX_NOTES) errors.add("La nota para Raúl supera los " + MAX_NOTES + " caracteres.");
        if (kit.templates().isEmpty() || kit.templates().size() > 12) {
            errors.add("El kit debe tener entre 1 y 12 plantillas (tiene " + kit.templates().size() + ").");
        }
        Set<String> ids = new HashSet<>();
        for (KitTemplate template : kit.templates()) {
            if (!ids.add(template.id())) errors.add("El id de plantilla «" + template.id() + "» está repetido.");
            template(template, errors);
        }
        return errors;
    }

    private static void palette(KitContent kit, List<String> errors) {
        if (kit.palette().size() < 3 || kit.palette().size() > 8) {
            errors.add("La paleta debe tener entre 3 y 8 colores (tiene " + kit.palette().size() + ").");
        }
        for (PaletteColor color : kit.palette()) {
            if (color.name().isBlank() || color.name().length() > 40) {
                errors.add("Cada color de la paleta necesita un nombre de hasta 40 caracteres.");
            }
            if (!HEX.matcher(color.hex()).matches()) {
                errors.add("El color «" + color.name() + "» tiene un valor no válido («" + color.hex() + "»): usa #RRGGBB.");
            }
        }
    }

    private void template(KitTemplate template, List<String> errors) {
        String label = "La plantilla «" + template.id() + "» ";
        if (!ID.matcher(template.id()).matches() || template.id().length() > 40) {
            errors.add(label + "tiene un id no válido: minúsculas, cifras y guiones, hasta 40 caracteres (por ejemplo «cita-4x5»).");
        }
        if (template.name().isBlank() || template.name().length() > 80) {
            errors.add(label + "necesita un nombre de hasta 80 caracteres.");
        }
        KitPiece piece = template.piece();
        if (!piece.active()) {
            errors.add(label + "es de la pieza «" + piece.code() + "», que todavía no está activa (piezas activas: "
                    + KitPiece.activeCodes() + ").");
            return;
        }
        if (template.svg().getBytes(StandardCharsets.UTF_8).length > MAX_SVG_BYTES) {
            errors.add(label + "supera el máximo de " + MAX_SVG_BYTES / 1024 + " KB.");
            return;
        }
        TemplateChecker.Result result = checker.check(template.svg());
        for (String error : result.errors()) errors.add(label + "(" + piece.code() + "): " + error);
        if (!result.errors().isEmpty()) return;
        origin(label, piece, template.svg(), errors);
        fields(label, piece, result, errors);
        ratio(label, piece, result, errors);
        image(label, piece, template.image(), result, errors);
    }

    private static void fields(String label, KitPiece piece, TemplateChecker.Result result, List<String> errors) {
        for (String required : piece.requiredFields()) {
            if (result.fixed().contains(required)) {
                errors.add(label + "tiene el campo «campo-" + required + "» como fijo (data-fijo): no se podría rellenar con el texto de la pieza.");
            } else if (!result.fields().contains(required)) {
                errors.add(label + "(" + piece.code() + ") necesita el campo «campo-" + required + "» (y su «caja-" + required + "»).");
            }
        }
        for (String field : result.fields()) {
            if (!piece.requiredFields().contains(field) && !piece.optionalFields().contains(field)) {
                errors.add(label + "(" + piece.code() + ") tiene el campo «campo-" + field + "», que esa pieza no usa. "
                        + "Campos de " + piece.code() + ": " + String.join(", ", piece.requiredFields())
                        + (piece.optionalFields().isEmpty() ? "" : " y, opcional, " + String.join(", ", piece.optionalFields()))
                        + ". Un texto fijo (la marca, una dirección) va con data-fijo=\"true\".");
            }
        }
    }

    /** El origen del viewBox tiene que ser 0 0: la zona de la ilustración se mide desde la esquina del lienzo. */
    private static void origin(String label, KitPiece piece, String svg, List<String> errors) {
        java.util.regex.Matcher m = VIEWBOX_ORIGIN.matcher(svg);
        if (m.find() && (Double.parseDouble(m.group(1)) != 0 || Double.parseDouble(m.group(2)) != 0)) {
            errors.add(label + "(" + piece.code() + ") tiene un viewBox que no empieza en «0 0» (empieza en «" + m.group(1) + " "
                    + m.group(2) + "»). Usa un viewBox como «0 0 1080 1350» y coloca el dibujo y las cajas dentro de él.");
        }
    }

    private static void ratio(String label, KitPiece piece, TemplateChecker.Result result, List<String> errors) {
        boolean fits = piece.ratios().stream().anyMatch(r -> r.matches(result.width(), result.height()));
        if (!fits) {
            errors.add(label + "(" + piece.code() + ") mide " + result.width() + "×" + result.height()
                    + ", que no tiene una proporción admitida: " + String.join(" o ", piece.ratios().stream().map(KitPiece.Ratio::label).toList())
                    + " (1080×1350 o 1080×1920).");
        }
    }

    private static void image(String label, KitPiece piece, ImageArea image, TemplateChecker.Result result, List<String> errors) {
        if (image == null) {
            if (piece.imageRequired()) {
                errors.add(label + "(" + piece.code() + ") necesita «image»: la zona donde va la ilustración, en unidades de su viewBox "
                        + "(la plantilla va encima de la ilustración de la publicación).");
            }
            return;
        }
        boolean finite = Double.isFinite(image.x()) && Double.isFinite(image.y())
                && Double.isFinite(image.width()) && Double.isFinite(image.height());
        if (!finite || image.width() <= 0 || image.height() <= 0) {
            errors.add(label + "tiene una zona de imagen con números no válidos (el ancho y el alto deben ser mayores que 0).");
        } else if (image.x() < 0 || image.y() < 0 || image.x() + image.width() > result.width() + 1
                || image.y() + image.height() > result.height() + 1) {
            errors.add(label + "tiene la zona de imagen fuera del lienzo (" + result.width() + "×" + result.height() + ").");
        }
    }
}
