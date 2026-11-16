package com.socialhub.platform.diagnostics;

import java.util.regex.Pattern;

/**
 * Tapa los secretos que podrían colarse en un registro: claves, tokens, contraseñas y cabeceras de autorización.
 * La misma expresión está en {@code application.yml} ({@code socialhub.logging.redaction-regex}), que es la que
 * usa Logback para {@code socialhub.log}; una prueba comprueba que son idénticas. Sin llaves ni comillas dentro
 * (las comillas son {@code \x22} y {@code \x27}) para poder ir en un patrón de Logback.
 */
public final class SecretRedactor {

    /** Cuatro casos: {@code nombre=valor} o {@code "nombre":"valor"}, {@code ?key=valor}, {@code Bearer valor} y formatos conocidos de clave. */
    public static final String REGEX =
            "(?i)(?:"
            + "\\b([\\w.-]*(?:token|secret|passw(?:or)?d|pwd|api[_-]?key|apikey|authorization|credentials?|private[_-]?key)"
            + "[\\x22\\x27]?\\s*[:=]\\s*[\\x22\\x27]?(?:(?:bearer|basic)\\s+)?)[^\\s&,;\\x22\\x27)\\]]+"
            + "|([?&](?:key|sig|signature|code|auth|apikey|api_key)=)[^&\\s\\x22\\x27]+"
            + "|\\b((?:bearer|basic)\\s+)[A-Za-z0-9._~+/=-]+"
            + "|\\b(?:AIza" + atLeast("[\\w-]", 20) + "|ya29\\.[\\w.-]+|1//" + atLeast("[\\w.-]", 20) + "|sk-" + atLeast("[\\w-]", 20)
            + "|gh[pousr]_" + atLeast("\\w", 20) + "|xox[abprs]-" + atLeast("[\\w-]", 10) + "|EAA" + atLeast("\\w", 30)
            + "|eyJ" + atLeast("[\\w-]", 8) + "\\." + atLeast("[\\w-]", 8) + "\\.[\\w-]*)"
            + ")";

    /** Lo que sustituye al secreto; conserva el nombre del campo para saber qué era. */
    public static final String REPLACEMENT = "$1$2$3***";

    /** «Al menos n veces», sin llaves ({n,}) porque las llaves rompen las opciones de un patrón de Logback. */
    private static String atLeast(String unit, int n) {
        return unit.repeat(n) + unit + "*";
    }

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private SecretRedactor() {}

    public static String redact(String text) {
        return text == null || text.isEmpty() ? text : PATTERN.matcher(text).replaceAll(REPLACEMENT);
    }
}
