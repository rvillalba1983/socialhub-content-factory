package com.socialhub.tools.domain;

import com.socialhub.platform.process.SafeProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

    /** La primera pista de vídeo del archivo. */
    com.socialhub.tools.Ffmpeg.VideoInfo probeVideo(Path media) throws InterruptedException;
}
