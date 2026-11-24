package com.socialhub.brandkit.domain;

import org.jspecify.annotations.Nullable;

/** Una plantilla SVG del kit ({@code docs/plantillas-svg.md}) para una pieza. */
public record KitTemplate(String id, KitPiece piece, String name, String svg, @Nullable ImageArea image) {}
