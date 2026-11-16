package com.socialhub.pieces.domain;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Lo que genera un spec: la carpeta de salida, sus piezas y el LEEME si lo pedía. */
public record PieceBatch(Path outputDir, List<PieceResult> pieces, @Nullable Path readme) {}
