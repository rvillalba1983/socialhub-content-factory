package com.socialhub.backup.domain;

import java.time.LocalDateTime;
import org.jmolecules.ddd.annotation.ValueObject;

/** Una copia guardada: su nombre, cuándo se hizo (hora local), su tamaño y si está en la carpeta local. */
@ValueObject
public record BackupFile(String name, LocalDateTime createdAt, long sizeBytes, BackupKind kind, boolean local) {}
