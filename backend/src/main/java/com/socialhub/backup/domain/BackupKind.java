package com.socialhub.backup.domain;

/** Por qué se hizo una copia. El sufijo va en el nombre del archivo. */
public enum BackupKind {
    DAILY("diaria"),
    MANUAL("manual"),
    BEFORE_RESTORE("antes-de-restaurar");

    private final String suffix;

    BackupKind(String suffix) { this.suffix = suffix; }

    public String suffix() { return suffix; }
}
