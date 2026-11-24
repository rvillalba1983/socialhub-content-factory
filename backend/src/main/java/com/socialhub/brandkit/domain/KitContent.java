package com.socialhub.brandkit.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Lo que Claude devuelve en {@code outputs.kit}: la paleta, una nota para Raúl y las plantillas. */
public record KitContent(List<PaletteColor> palette, @Nullable String notes, List<KitTemplate> templates) {}
