package com.socialhub.promotion.domain;

/** Papel que hace una carpeta de Drive en una marca o promoción. */
public enum FolderRole {
    MATERIAL("Material"),
    DRAFTS("Borradores"),
    GENERATED("Generados"),
    APPROVED("Aprobados"),
    PUBLISHED("Publicados"),
    REPORTS("Informes"),
    ORDERS("Encargos");

    private final String label;

    FolderRole(String label) { this.label = label; }

    public String label() { return label; }
}
