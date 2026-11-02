package com.socialhub.production.domain;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Quién hace una tarea: un modelo de IA, una herramienta local, Raúl, el navegador o un conector.
 *
 * @param name nombre visible (para la IA, el modelo: Haiku, Sonnet u Opus)
 */
@ValueObject
public record Agent(AgentKind kind, String name) {

    public Agent {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(name);
    }

    public static Agent ai(AiModel model) { return new Agent(AgentKind.AI, model.label()); }

    public static Agent raul() { return new Agent(AgentKind.HUMAN, "Raúl"); }

    public boolean isAi() { return kind == AgentKind.AI; }

    public String label() { return isAi() ? "Claude " + name : name; }
}
