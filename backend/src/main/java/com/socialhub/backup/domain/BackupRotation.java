package com.socialhub.backup.domain;

import java.util.Comparator;
import java.util.List;

/** Qué copias sobran en una carpeta: se guardan las {@value #KEEP} más recientes, sean del tipo que sean. */
public final class BackupRotation {

    public static final int KEEP = 14;

    private BackupRotation() {}

    public static List<BackupFile> surplus(List<BackupFile> files) {
        return files.stream()
                .sorted(Comparator.comparing(BackupFile::createdAt).reversed())
                .skip(KEEP)
                .toList();
    }
}
