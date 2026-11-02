package com.socialhub.publishing.domain;

/** Motivo fijo de un rechazo. */
public enum RejectionReason {
    TONE("Tono"),
    REPEATED("Repetido"),
    OFF_BRAND("Fuera de la marca"),
    WRONG_DATA("Error de datos"),
    FORMAT("Formato"),
    OTHER("Otro");

    private final String label;

    RejectionReason(String label) { this.label = label; }

    public String label() { return label; }
}
