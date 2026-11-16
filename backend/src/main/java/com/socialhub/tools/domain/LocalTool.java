package com.socialhub.tools.domain;

import java.util.List;

/** Una herramienta local que SocialHub sabe usar. La implementa cada adaptador de salida. */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface LocalTool {

    /** Identificador estable, por ejemplo {@code ffmpeg}. */
    String id();

    String name();

    String description();

    /** Licencia con la que se usa. */
    String license();

    List<String> capabilities();

    /** Comprueba que funciona en este ordenador. No lanza excepciones: el fallo va en el resultado. */
    Check check() throws InterruptedException;

    record Check(boolean ok, String detail) {}
}
