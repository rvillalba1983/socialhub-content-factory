package com.socialhub.production.domain;

/** Modelos de Claude que puede usar una tarea. */
public enum AiModel {
    HAIKU("Haiku"), SONNET("Sonnet"), OPUS("Opus");

    private final String label;

    AiModel(String label) { this.label = label; }

    public String label() { return label; }
}
