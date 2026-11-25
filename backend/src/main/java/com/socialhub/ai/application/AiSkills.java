package com.socialhub.ai.application;

import com.socialhub.ai.domain.AiRequestType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Qué skills de Claude hacen falta en cada encargo (fase 13, bloque 4): solo las necesarias según los tipos de pieza de
 * las publicaciones, para gastar menos tokens. Viajan en {@code inputs.skills}; la tarea de la nube las carga con la
 * herramienta Skill y, si alguna no existe en su sesión, sigue sin ella y lo dice en {@code warnings}. Las reglas fijas
 * del bloque van aparte, en {@link AiStandingRules}, para que viajen aunque la skill no exista.
 * <p>
 * {@code infographic-and-data-viz} y {@code meme-and-culture} no se activan para El DeLito (decisión 13), y
 * {@code thumbnail-design} y {@code pinterest-pin-design} quedan fuera (decisión 2): no están en ninguna lista.
 */
final class AiSkills {

    static final String BRAND_PROFILE = "brand-profile";
    static final String CAROUSEL = "carousel-writer";
    static final String STORY = "story-writer";
    static final String TIKTOK_PHOTO = "tiktok-photo-mode";
    static final String QUOTES = "quote-cards-and-text-graphics";
    static final String HOOK = "hook-writer";
    static final String CAPTION = "caption-writer";
    static final String PLATFORM_SPECS = "platform-specs-and-validation";
    static final String DESIGN = "design-and-templates";
    static final String DATAVIZ = "dataviz";
    static final String FRONTEND = "frontend-design";

    /** Nota que acompaña a la lista: cómo se cargan y qué hacer si faltan. */
    static final String NOTE = "Carga estas skills con la herramienta Skill, en este orden, antes de empezar. «brand-profile» "
            + "sin entrevista: parte de inputs.brand_identity (si falta el tono o el público, needs_human). Si alguna no "
            + "existe en tu sesión, sigue sin ella (con las reglas de este encargo) y avísalo en «warnings». De cada skill "
            + "usa solo lo de texto, estructura y tamaños: se ignora todo lo de generar imágenes con IA, Pinterest y YouTube.";

    private AiSkills() {}

    /**
     * @param handlerInputs lo que ha añadido el manejador del tipo: de «publications» (o «publication») salen los tipos de pieza
     * @return las skills del encargo, sin repetir y en orden de carga; vacío si ese tipo de encargo no usa ninguna
     */
    static List<String> forOrder(AiRequestType type, Map<String, Object> handlerInputs) {
        Set<String> skills = new LinkedHashSet<>();
        switch (type) {
            case WEEKLY_SPEC -> {
                skills.add(BRAND_PROFILE);
                for (Map<?, ?> publication : publications(handlerInputs)) pieceSkills(publication).forEach(skills::add);
                skills.add(HOOK);
                skills.add(PLATFORM_SPECS);
            }
            case CAPTIONS -> skills.addAll(List.of(BRAND_PROFILE, CAPTION, HOOK, PLATFORM_SPECS));
            case QUALITY_REVIEW -> skills.add(PLATFORM_SPECS);
            case BRAND_KIT -> skills.addAll(List.of(BRAND_PROFILE, DESIGN, DATAVIZ, FRONTEND));
            default -> { }
        }
        return new ArrayList<>(skills);
    }

    /** Las de escritura de la pieza de esa publicación, según su tipo y su red. */
    private static List<String> pieceSkills(Map<?, ?> publication) {
        String type = normalize(publication.get("type"));
        boolean tiktok = "tiktok".equals(normalize(publication.get("platform")));
        List<String> skills = new ArrayList<>();
        if (type.contains("carrusel") || type.contains("carousel")) skills.add(tiktok ? TIKTOK_PHOTO : CAROUSEL);
        if (type.contains("historia") || type.contains("story")) skills.add(STORY);
        if (type.contains("cita") || type.contains("quote")) skills.add(QUOTES);
        return skills;
    }

    private static List<Map<?, ?>> publications(Map<String, Object> inputs) {
        List<Map<?, ?>> list = new ArrayList<>();
        if (inputs.get("publications") instanceof List<?> many) {
            many.forEach(item -> { if (item instanceof Map<?, ?> m) list.add(m); });
        }
        if (inputs.get("publication") instanceof Map<?, ?> one) list.add(one);
        return list;
    }

    private static String normalize(Object text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
