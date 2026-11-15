package com.socialhub.pieces.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Lee un spec.json dentro de las carpetas de la promoción. */
@SecondaryPort
public interface SpecLoader {

    /**
     * @param specPath ruta completa del spec.json
     * @param folders carpetas de la promoción, fuera de las cuales no se lee ni se escribe nada
     * @throws IllegalArgumentException si el archivo no se puede leer o no es un spec
     */
    LoadedSpec read(String specPath, List<String> folders);
}
