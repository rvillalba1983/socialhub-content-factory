package com.socialhub.promotion.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Identificadores legibles a partir de un nombre: «Lanzamiento del poemario» → «lanzamiento-del-poemario». */
public final class Slugs {

    private Slugs() {}

    public static String of(String name, Predicate<String> taken) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = "elemento";
        if (base.length() > 60) base = base.substring(0, 60).replaceAll("-+$", "");
        String candidate = base;
        for (int n = 2; taken.test(candidate); n++) candidate = base + "-" + n;
        return candidate;
    }
}
