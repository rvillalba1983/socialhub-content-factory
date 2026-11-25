package com.socialhub.publishing.domain;

import java.nio.file.Path;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Mira dentro de un archivo de vídeo (tamaño y si tiene imagen), para las comprobaciones de calidad. */
@SecondaryPort
public interface VideoInspector {

    /**
     * @param readable si se pudo leer el archivo
     * @param hasVideo si tiene pista de vídeo
     */
    record Info(boolean readable, boolean hasVideo, int width, int height) {}

    /** Lo que hay en el vídeo; vacío si no se puede mirar (sin ffprobe): entonces no se avisa de nada. */
    Optional<Info> inspect(Path file);
}
