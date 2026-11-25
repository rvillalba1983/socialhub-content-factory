package com.socialhub.pieces.domain;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Una pieza ya generada. {@code publication}: publicación de SocialHub a la que van sus archivos, si el spec la indica.
 * {@code sources}: de dónde sale cada archivo (página del PDF y archivo de imagen, hallazgo K), si el spec lo dice.
 * {@code sound}: sonido que pide el spec para la publicación, si lo pide.
 */
public record PieceResult(String name, String type, @Nullable String publication, List<Path> files, List<Source> sources,
                          PromoSpec.@Nullable Sound sound) {

    /** De dónde sale un archivo generado: la página del PDF del libro y el nombre del archivo de imagen que usa. */
    public record Source(Path file, @Nullable Integer page, @Nullable String imageName) {}

    public PieceResult(String name, String type, @Nullable String publication, List<Path> files) {
        this(name, type, publication, files, List.of(), null);
    }

    /** El origen de un archivo de esta pieza, si se conoce. */
    public PieceResult.@Nullable Source sourceOf(Path file) {
        return sources.stream().filter(s -> s.file().equals(file)).findFirst().orElse(null);
    }
}
