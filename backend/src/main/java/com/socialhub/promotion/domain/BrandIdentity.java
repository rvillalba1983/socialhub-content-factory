package com.socialhub.promotion.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Identidad de una marca. Sus promociones la heredan; va en cada encargo a Claude y el control de calidad comprueba
 * las expresiones prohibidas, la llamada a la acción y los hashtags fijos.
 */
@ValueObject
public record BrandIdentity(String tone, String audience, List<String> forbiddenTopics, List<String> preferredExpressions,
                            List<String> forbiddenExpressions, List<String> colors, String font, String logo,
                            String visualStyle, String photoStyle, String callToAction, String fixedHashtags) {

    public static final BrandIdentity EMPTY = new BrandIdentity("", "", List.of(), List.of(), List.of(), List.of(), "", "",
            "", "", "", "");

    public BrandIdentity {
        tone = clean(tone);
        audience = clean(audience);
        forbiddenTopics = clean(forbiddenTopics);
        preferredExpressions = clean(preferredExpressions);
        forbiddenExpressions = clean(forbiddenExpressions);
        colors = clean(colors);
        font = clean(font);
        logo = clean(logo);
        visualStyle = clean(visualStyle);
        photoStyle = clean(photoStyle);
        callToAction = clean(callToAction);
        fixedHashtags = clean(fixedHashtags);
    }

    /** Solo los campos rellenos, con nombres claros para Claude. */
    public Map<String, Object> forAi() {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "tone", tone);
        put(m, "audience", audience);
        put(m, "forbidden_topics", forbiddenTopics);
        put(m, "preferred_expressions", preferredExpressions);
        put(m, "forbidden_expressions", forbiddenExpressions);
        put(m, "colors", colors);
        put(m, "font", font);
        put(m, "logo", logo);
        put(m, "visual_style", visualStyle);
        put(m, "photo_style", photoStyle);
        put(m, "call_to_action", callToAction);
        put(m, "fixed_hashtags", fixedHashtags);
        return m;
    }

    private static void put(Map<String, Object> m, String key, Object value) {
        if (value instanceof String s && !s.isEmpty() || value instanceof List<?> l && !l.isEmpty()) m.put(key, value);
    }

    private static String clean(String s) { return s == null ? "" : s.strip(); }

    private static List<String> clean(List<String> list) {
        return list == null ? List.of() : list.stream().map(String::strip).filter(x -> !x.isEmpty()).distinct().toList();
    }
}
