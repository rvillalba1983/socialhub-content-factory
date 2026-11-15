package com.socialhub.pieces.domain;

import java.nio.file.Path;

/** Un spec.json leído y validado: su ruta real, su contenido y el acceso a los archivos de la promoción. */
public record LoadedSpec(Path path, PromoSpec spec, PieceFiles files) {}
