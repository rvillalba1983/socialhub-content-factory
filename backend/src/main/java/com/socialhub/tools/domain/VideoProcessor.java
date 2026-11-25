package com.socialhub.tools.domain;

import com.socialhub.platform.process.SafeProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** El programa de vídeo que SocialHub lleva incluido (FFmpeg): ejecutarlo y medir la duración de un archivo. */
@SecondaryPort
public interface VideoProcessor {

    /** Ruta del ejecutable. */
    Path executable();

    /** Ruta de ffprobe, que va junto al ejecutable. */
    Path ffprobe();

    /** Ejecuta el programa con argumentos ya validados por quien lo llama. */
    SafeProcess.Result run(String operation, List<String> arguments, Duration timeout) throws InterruptedException;

    /** Duración en segundos de un audio o vídeo. */
    double probeDuration(Path media) throws InterruptedException;

    /** La primera pista de vídeo del archivo, con la rotación aplicada; vacío si no se pudo comprobar (plazo agotado o fallo al lanzar). */
    Optional<VideoInfo> probeVideo(Path media, Duration timeout) throws InterruptedException;

    /** Lo que ffprobe ve en la primera pista de vídeo: si se pudo leer, si tiene vídeo y su tamaño. */
    record VideoInfo(boolean readable, boolean hasVideo, int width, int height) {}
}
