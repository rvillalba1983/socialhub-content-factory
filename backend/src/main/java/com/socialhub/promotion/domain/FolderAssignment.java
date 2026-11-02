package com.socialhub.promotion.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * La carpeta que hace un papel.
 *
 * @param origin de dónde sale: de la marca o de la propia promoción
 */
@ValueObject
public record FolderAssignment(FolderRole role, String location, Origin origin) {

    public enum Origin { BRAND, PROMOTION }
}
