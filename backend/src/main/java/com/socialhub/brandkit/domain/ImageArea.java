package com.socialhub.brandkit.domain;

/** Dónde va la ilustración, en unidades del {@code viewBox} de la plantilla. La plantilla se pinta encima. */
public record ImageArea(double x, double y, double width, double height) {}
