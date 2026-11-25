package com.socialhub.tools;

import com.socialhub.platform.process.SafeProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** FFmpeg incluido en la aplicación, para los módulos que montan vídeo. */
@PrimaryPort
public interface Ffmpeg {

    /** Ejecuta FFmpeg con argumentos ya validados por quien lo llama. */
    SafeProcess.Result run(String operation, List<String> arguments, Duration timeout) throws InterruptedException;

    /** Duración en segundos de un audio o vídeo, con ffprobe. */
    double probeDuration(Path media) throws InterruptedException;

    /**
     * Lo que ffprobe ve en la primera pista de vídeo de un archivo.
     *
     * @param readable   si ffprobe pudo leer el archivo
     * @param hasVideo   si tiene pista de vídeo con tamaño
     */
    record VideoInfo(boolean readable, boolean hasVideo, int width, int height) {}

    /**
     * La pista de vídeo del archivo (tamaño ya con la rotación aplicada). Si ffprobe termina con error, el archivo es «no
     * legible»; si no se pudo lanzar o se agotó el plazo no se sabe nada y el resultado es vacío.
     */
    Optional<VideoInfo> probeVideo(Path media, Duration timeout) throws InterruptedException;
}
