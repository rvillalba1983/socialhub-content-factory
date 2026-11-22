package com.socialhub.publishing.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Un fragmento citado en una publicación, tal como lo declara Claude.
 *
 * @param text el fragmento, que tiene que aparecer literalmente en el documento
 * @param sourceFile ruta del documento (completa, o relativa a una carpeta de la promoción)
 * @param location página o línea que dice Claude, si la sabe
 */
@ValueObject
public record Citation(String text, String sourceFile, @Nullable String location) {}
