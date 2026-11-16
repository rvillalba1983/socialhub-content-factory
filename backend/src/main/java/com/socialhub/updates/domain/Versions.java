package com.socialhub.updates.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versiones de tres números («1.2.7»), con una «v» opcional delante. */
public final class Versions {

    private static final Pattern PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    private Versions() {}

    /** Los tres números, o vacío si no es una versión (por ejemplo «desconocida»). */
    public static Optional<int[]> parse(String text) {
        Matcher m = PATTERN.matcher(text.strip());
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Negativo si {@code a} es anterior a {@code b}. Las dos tienen que ser versiones válidas. */
    public static int compare(String a, String b) {
        int[] x = parse(a).orElseThrow();
        int[] y = parse(b).orElseThrow();
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        return 0;
    }

    /** El número de versión sin la «v» (por ejemplo «v1.2.0» → «1.2.0»). */
    public static String clean(String text) { return text.strip().replaceFirst("^v", ""); }
}
