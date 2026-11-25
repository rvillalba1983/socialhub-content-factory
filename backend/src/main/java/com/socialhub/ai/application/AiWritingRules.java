package com.socialhub.ai.application;

import com.socialhub.ai.domain.AiRequestType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lo que SocialHub añade a los encargos que escriben textos públicos (spec y textos): la llamada a la acción de la marca
 * con sus varias formulaciones (decisión 47) y los hashtags de cada red (hallazgo 23-19).
 */
final class AiWritingRules {

    static final String CTA = "Llamada a la acción: la de la marca está en inputs.brand_identity.call_to_action (con varias "
            + "formulaciones en call_to_action_options). Manda la de la marca, no la de otro sitio (ni el enlace de una tienda "
            + "si la marca usa otro). Cada texto lleva UNA de las formulaciones, copiada tal cual, y en publicaciones distintas "
            + "usa formulaciones distintas: no repitas la misma en todas ni dos seguidas. Si la marca solo tiene una, úsala tal "
            + "cual. No la pongas donde el texto ya dice lo mismo.";

    static final String HASHTAGS_NOTE = "Hashtags de cada red: usa solo etiquetas de la red de la publicación (campo «platform»), "
            + "nunca las de otra, y mantén los hashtags fijos de la marca si los hay.";

    static final Map<String, String> HASHTAGS = Map.of(
            "INSTAGRAM", "Instagram: hasta 30 etiquetas (mejor entre 5 y 12), mezcla de temáticas (#bookstagram, #poesia…) y de la marca. "
                    + "Sin etiquetas propias de TikTok (#BookTok, #fyp).",
            "TIKTOK", "TikTok: entre 3 y 6 etiquetas cortas y de TikTok (#BookTok, #poesia…). Sin etiquetas propias de Instagram "
                    + "(#bookstagram, #instagood, #reels).");

    private AiWritingRules() {}

    /** Las formulaciones de la llamada a la acción: una por línea. */
    static List<String> ctaOptions(String callToAction) {
        List<String> options = new ArrayList<>();
        for (String line : (callToAction == null ? "" : callToAction).split("\\R")) {
            String clean = line.strip();
            if (!clean.isEmpty() && !options.contains(clean)) options.add(clean);
        }
        return options;
    }

    /** Datos que se añaden a «inputs» de los encargos de spec y textos. */
    static Map<String, Object> forOrder(AiRequestType type, Map<String, Object> identity, Map<String, Object> handlerInputs) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (type != AiRequestType.WEEKLY_SPEC && type != AiRequestType.CAPTIONS) return extra;
        if (identity.get("call_to_action") instanceof String cta && !cta.isBlank()) {
            extra.put("call_to_action_options", ctaOptions(cta));
            extra.put("call_to_action_rule", CTA);
        }
        if (type == AiRequestType.CAPTIONS) {
            Map<String, String> byNetwork = new LinkedHashMap<>();
            if (handlerInputs.get("publications") instanceof List<?> many) {
                for (Object item : many) {
                    if (item instanceof Map<?, ?> m && m.get("platform") != null) {
                        String platform = m.get("platform").toString().toUpperCase(Locale.ROOT);
                        if (HASHTAGS.containsKey(platform)) byNetwork.put(platform, HASHTAGS.get(platform));
                    }
                }
            }
            if (!byNetwork.isEmpty()) {
                extra.put("hashtags_by_network", byNetwork);
                extra.put("hashtags_rule", HASHTAGS_NOTE);
            }
        }
        return extra;
    }
}
