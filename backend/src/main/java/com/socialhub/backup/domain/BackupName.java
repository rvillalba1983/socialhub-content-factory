package com.socialhub.backup.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Nombre de una copia: {@code socialhub-2026-11-08-150012-diaria.zip} (fecha y hora local, y el motivo). Solo se
 * tratan como copias los archivos con este formato, así que SocialHub nunca toca otros archivos de la carpeta.
 */
@ValueObject
public record BackupName(LocalDateTime createdAt, BackupKind kind) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final Pattern NAME = Pattern.compile("socialhub-(\\d{4}-\\d{2}-\\d{2}-\\d{6})-([a-z-]+)\\.zip");

    public String value() {
        return "socialhub-" + FORMAT.format(createdAt) + "-" + kind.suffix() + ".zip";
    }

    public static Optional<BackupName> parse(String name) {
        Matcher m = NAME.matcher(name);
        if (!m.matches()) return Optional.empty();
        Optional<BackupKind> kind = Arrays.stream(BackupKind.values()).filter(k -> k.suffix().equals(m.group(2))).findFirst();
        if (kind.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new BackupName(LocalDateTime.parse(m.group(1), FORMAT), kind.get()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
