package com.socialhub.ai;

/** Modelos de Claude, de menos a más capaz. */
public enum ClaudeModel {
    HAIKU, SONNET, OPUS;

    /** Un nivel más arriba (Opus se queda en Opus). */
    public ClaudeModel up() { return this == OPUS ? OPUS : values()[ordinal() + 1]; }

    public static ClaudeModel max(ClaudeModel a, ClaudeModel b) { return a.ordinal() >= b.ordinal() ? a : b; }

    public String label() { return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT); }
}
