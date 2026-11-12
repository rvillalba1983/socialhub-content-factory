package com.socialhub.publishing.adapter.out.instagram;

import java.util.Map;

/**
 * Los errores más comunes de Meta, en español. El texto original (en inglés si no se pide otro idioma) queda solo en el
 * registro; a Raúl se le enseña esto.
 */
final class MetaErrors {

    private static final Map<Integer, String> BY_SUBCODE = Map.ofEntries(
            Map.entry(33, "Meta no encuentra esa cuenta o la clave no tiene acceso a ella"),
            Map.entry(2207003, "Meta tardó demasiado en descargar el archivo del enlace público"),
            Map.entry(2207004, "la imagen es demasiado grande para Instagram"),
            Map.entry(2207005, "el formato de la imagen no vale para Instagram (tiene que ser JPEG)"),
            Map.entry(2207006, "Meta no encuentra el archivo del enlace público (¿sigue compartido en «publico»?)"),
            Map.entry(2207009, "la proporción de la imagen no la admite Instagram"),
            Map.entry(2207010, "el texto es demasiado largo para Instagram"),
            Map.entry(2207020, "el contenido preparado en Meta ha caducado"),
            Map.entry(2207026, "el formato del vídeo no lo admite Instagram"),
            Map.entry(2207027, "el contenido aún no está listo en Meta"),
            Map.entry(2207028, "Meta rechaza alguno de los elementos del carrusel"),
            Map.entry(2207032, "Meta no pudo preparar el contenido"),
            Map.entry(2207042, "se ha alcanzado el límite diario de publicaciones de Instagram"),
            Map.entry(2207050, "la cuenta de Instagram está restringida por Meta"),
            Map.entry(2207051, "Instagram ha bloqueado la publicación por parecer spam"));

    private MetaErrors() {}

    /** El mensaje en español de un error de Meta. */
    static String translate(int httpCode, int code, int subcode) {
        String detail = BY_SUBCODE.get(subcode);
        if (detail != null) return "Meta no la acepta: " + detail;
        if (code == 10 || (code >= 200 && code <= 299)) {
            return "a la clave le faltan permisos (hacen falta instagram_basic e instagram_content_publish) o la cuenta no está asignada a la clave";
        }
        return switch (code) {
            case 4, 17, 32, 613, 80002, 80004 -> "Meta limita las llamadas (demasiadas peticiones seguidas): espera un rato";
            case 100 -> "Meta no acepta lo enviado (un dato no es válido o la cuenta no es la correcta)";
            case 368 -> "Meta ha bloqueado temporalmente la acción por ir contra sus normas";
            case 9004 -> "Meta no pudo descargar el archivo del enlace público (¿sigue siendo público?)";
            case 9007 -> "el contenido aún no está listo para publicarse";
            case 36000, 36001, 36003 -> "Meta no acepta el contenido (formato o proporción no válidos para Instagram)";
            default -> httpCode / 100 == 5
                    ? "Meta tuvo un fallo interno (error " + httpCode + ")"
                    : "Meta respondió con un error (código " + code + (subcode == 0 ? "" : ", subcódigo " + subcode)
                            + "): el detalle está en «Operaciones externas»";
        };
    }
}
