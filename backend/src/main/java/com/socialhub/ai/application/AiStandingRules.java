package com.socialhub.ai.application;

import com.socialhub.ai.domain.AiRequestType;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas fijas que SocialHub añade a las «rules» de cada encargo, además de las de la marca y la promoción. Viven aquí
 * (y no solo en la tarea programada de Claude) para que lleguen a Claude dentro del propio encargo.
 */
final class AiStandingRules {

    /** Máximo de respuestas propuestas que enseña la tarjeta. */
    static final int MAX_OPTIONS = 6;

    private AiStandingRules() {}

    static final String ASK_HUMAN = "Si necesitas que Raúl decida o aclare algo, no lo inventes: devuelve status «needs_human» "
            + "con la pregunta clara en «error» y, si hay respuestas posibles, en «options» de 2 a " + MAX_OPTIONS
            + " textos cortos para elegir. Raúl contesta desde la tarjeta de la tarea y el encargo vuelve a salir.";

    static final String USE_ANSWERS = "Si inputs.human_answers trae respuestas de Raúl ([{question, answer, answered_at}]), "
            + "úsalas como instrucciones del propio Raúl y no vuelvas a preguntar lo que ya contestó.";

    static final String CITATIONS = "Citas: si una pieza o un texto necesita un fragmento (versos del libro, letra, texto), "
            + "elígelo copiándolo LITERAL de un documento de las carpetas de la promoción (inputs.folders, por ejemplo la "
            + "carpeta «info»): nunca de memoria, ni inventado, ni parafraseado. Devuelve por cada pieza «citations»: "
            + "[{text, source_file, location}], con el fragmento tal cual está en el documento, en «source_file» la RUTA COMPLETA "
            + "del documento (la carpeta de inputs.folders unida al nombre del archivo, por ejemplo «G:/…/info/page 11-finalv2.pdf»), "
            + "solo la ruta: sin paréntesis, ni «Google Drive», ni identificadores ni otros añadidos, y en «location» la página o línea si la conoces. En socialhub.weekly-spec va dentro de cada "
            + "pieza del spec (piezas[].citations); en socialhub.captions, dentro de cada elemento de outputs.publications. "
            + "Si no encuentras el fragmento en ningún documento, no lo inventes ni lo cambies: devuelve «needs_human» "
            + "preguntando a Raúl qué fragmento o documento usar. SocialHub comprobará que cada cita aparece literalmente.";

    static final String NO_AI_IMAGES = "Skills: ignora todo lo que proponen para generar imágenes con IA (prompts para Nano Banana, "
            + "Ideogram o similares): en SocialHub no hay imágenes generadas con IA. Y ignora los formatos de Pinterest y de YouTube "
            + "(pines, miniaturas): SocialHub solo publica en Instagram y TikTok.";

    static final String LITERAL_VERSES = "Versos y letras, siempre literales y con «citations» (también en tarjetas de cita): copiados de un "
            + "documento de la promoción, nunca de memoria ni parafraseados.";

    static final String BRAND_COLOR = "Color de marca obligatorio: las piezas llevan los colores de la marca (inputs.brand_identity.colors); "
            + "si la marca no tiene color, no se dibuja nada con color inventado.";

    /** Cualquier encargo: una pasada que no hace nada no puede quedarse muda. */
    static final String NEVER_SILENT = "Nunca termines un encargo sin dejar su resultado: si no lo haces (no hay acceso a las carpetas, "
            + "falta algo, ya estaba hecho…), crea igualmente el resultado con status «failed» (o «needs_human» si decide Raúl) y el motivo "
            + "claro en «error». Un encargo que no hace nada y no dice por qué es el peor caso.";

    /** Los encargos que usan skills y llevan las reglas fijas del bloque 4. */
    static boolean usesSkills(AiRequestType type) {
        return type == AiRequestType.WEEKLY_SPEC || type == AiRequestType.CAPTIONS || type == AiRequestType.BRAND_KIT
                || type == AiRequestType.QUALITY_REVIEW;
    }

    /** Los encargos que generan textos o especificaciones de piezas: los que pueden necesitar citar un fragmento. */
    static boolean quotes(AiRequestType type) {
        return type == AiRequestType.WEEKLY_SPEC || type == AiRequestType.CAPTIONS;
    }

    static List<String> forType(AiRequestType type) {
        List<String> rules = new ArrayList<>(List.of(ASK_HUMAN, USE_ANSWERS, NEVER_SILENT));
        if (usesSkills(type)) rules.addAll(List.of(NO_AI_IMAGES, LITERAL_VERSES, BRAND_COLOR));
        if (quotes(type)) rules.add(CITATIONS);
        return rules;
    }
}
