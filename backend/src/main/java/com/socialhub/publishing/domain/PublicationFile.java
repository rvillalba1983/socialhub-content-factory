package com.socialhub.publishing.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Un archivo asociado a una publicación.
 *
 * @param origin quién lo asoció: el spec.json al generar las piezas o Raúl a mano
 */
@ValueObject
public record PublicationFile(String path, Origin origin, Instant addedAt) {

    public enum Origin { SPEC, MANUAL }

    /** Qué se puede enseñar del archivo. */
    public enum Kind { IMAGE, VIDEO, OTHER }

    /** Tipos MIME que se sirven para la vista previa, por extensión. */
    private static final Map<String, String> MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "gif", "image/gif",
            "mp4", "video/mp4", "m4v", "video/mp4", "mov", "video/quicktime", "webm", "video/webm");

    /** Identificador estable y opaco (no revela la ruta) con el que la vista previa pide el archivo. */
    public String id() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tipo MIME según la extensión, o {@code null} si no se puede enseñar. */
    public @org.jspecify.annotations.Nullable String mimeType() {
        int dot = path.lastIndexOf('.');
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (dot < 0 || dot < sep) return null;
        return MIME.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public Kind kind() {
        String mime = mimeType();
        if (mime == null) return Kind.OTHER;
        return mime.startsWith("image/") ? Kind.IMAGE : Kind.VIDEO;
    }
}
