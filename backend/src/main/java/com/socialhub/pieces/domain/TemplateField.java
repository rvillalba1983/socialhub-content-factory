package com.socialhub.pieces.domain;

/** Un campo de texto de una plantilla SVG: su nombre (sin el prefijo {@code campo-}), su caja y el texto de ejemplo. */
public record TemplateField(String name, double x, double y, double width, double height, String example, boolean fixed) {}
