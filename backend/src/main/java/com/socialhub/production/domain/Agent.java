package com.socialhub.production.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Quién hace una tarea: un modelo de IA, una herramienta local, Raúl, el navegador o un conector.
 *
 * @param kind ai, local, human, browser o conn
 * @param name nombre visible (para la IA, el modelo: Haiku, Sonnet u Opus)
 */
public record Agent(String kind, String name) {

    public static final Set<String> KINDS = Set.of("ai", "local", "human", "browser", "conn");
    public static final Set<String> AI_MODELS = Set.of("Haiku", "Sonnet", "Opus");

    public Agent {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(name);
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Tipo de agente desconocido: " + kind);
    }

    public static Agent ai(String model) {
        if (!AI_MODELS.contains(model)) throw new IllegalArgumentException("Modelo no disponible: " + model);
        return new Agent("ai", model);
    }

    public static Agent raul() { return new Agent("human", "Raúl"); }

    public boolean isAi() { return "ai".equals(kind); }

    public String label() { return isAi() ? "Claude " + name : name; }
}
