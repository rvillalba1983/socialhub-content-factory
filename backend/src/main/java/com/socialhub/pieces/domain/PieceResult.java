package com.socialhub.pieces.domain;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Una pieza ya generada. {@code publication}: publicación de SocialHub a la que van sus archivos, si el spec la indica. */
public record PieceResult(String name, String type, @Nullable String publication, List<Path> files) {}
