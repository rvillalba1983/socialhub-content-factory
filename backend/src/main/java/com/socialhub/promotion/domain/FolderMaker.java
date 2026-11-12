package com.socialhub.promotion.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Crea carpetas en el disco (las de Drive para ordenador). Nunca borra ni mueve nada. */
@SecondaryPort
public interface FolderMaker {

    /** Si no existe, crea la carpeta (y las de encima). Devuelve true si la ha creado. */
    boolean ensure(String location);

    /** Si se puede trabajar en esa ruta: su unidad existe y, si la ruta ya existe, es una carpeta. */
    boolean reachable(String location);
}
