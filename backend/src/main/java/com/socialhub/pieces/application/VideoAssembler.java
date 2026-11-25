package com.socialhub.pieces.application;

import com.socialhub.tools.Ffmpeg;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Las operaciones de FFmpeg del montaje por escenas, según la guía de montaje de vídeo: una escena por archivo y todas con
 * los mismos parámetros (tamaño, fotogramas por segundo, códec y formato de píxel, sin audio), unidas con {@code concat} sin
 * recodificar, y el sonido después, sobre el vídeo ya unido (el vídeo se copia sin recodificar).
 */
final class VideoAssembler {

    static final int FPS = 30;
    /** Duración del fundido a negro de entrada o de salida de una escena, en segundos. */
    static final double FADE_SECONDS = 0.4;
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final Ffmpeg ffmpeg;

    VideoAssembler(Ffmpeg ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /**
     * Una escena: una imagen fija durante {@code seconds}. Los fundidos (a negro, de entrada y/o de salida) se aplican aquí,
     * dentro de la escena, para que la unión posterior sea un simple corte.
     */
    void encodeScene(Path image, double seconds, boolean fadeIn, boolean fadeOut, Path target) throws InterruptedException {
        List<String> filters = new ArrayList<>();
        if (fadeIn) filters.add("fade=t=in:st=0:d=" + num(FADE_SECONDS));
        if (fadeOut) filters.add("fade=t=out:st=" + num(seconds - FADE_SECONDS) + ":d=" + num(FADE_SECONDS));
        filters.add("format=yuv420p");
        ffmpeg.run("scene", List.of("-y", "-loglevel", "error", "-loop", "1", "-framerate", String.valueOf(FPS),
                "-t", num(seconds), "-i", image.toString(), "-vf", String.join(",", filters), "-r", String.valueOf(FPS),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-g", String.valueOf(FPS),
                "-bf", "0", "-an", "-movflags", "+faststart", target.toString()), TIMEOUT);
    }

    /** Une las escenas, en orden, sin recodificar. */
    void concat(List<Path> scenes, Path target) throws IOException, InterruptedException {
        Path list = target.resolveSibling(target.getFileName() + ".txt");
        try {
            List<String> lines = new ArrayList<>();
            for (Path scene : scenes) {
                String name = scene.toString();
                if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException("La ruta de una escena tiene un salto de línea y FFmpeg no puede unirla: " + name.replaceAll("[\\r\\n]", " "));
                }
            }
            for (Path scene : scenes) lines.add("file '" + scene.toAbsolutePath().toString().replace("\\", "/").replace("'", "'\\''") + "'");
            Files.write(list, lines, StandardCharsets.UTF_8);
            ffmpeg.run("concat", List.of("-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", "-movflags", "+faststart", target.toString()), TIMEOUT);
        } finally {
            Files.deleteIfExists(list);
        }
    }

    /**
     * Pone el audio al vídeo (sustituye el que tuviera): desde {@code start} segundos del archivo de audio, con fundido de
     * entrada y de salida, y recortado a la duración del vídeo; si el audio es más corto, el vídeo sigue en silencio. El
     * vídeo no se recodifica.
     */
    void addAudio(Path video, Path audio, double start) throws IOException, InterruptedException {
        double total = ffmpeg.probeDuration(video);
        double available = ffmpeg.probeDuration(audio) - start;
        double effective = total <= 0 ? available : available > 0 ? Math.min(total, available) : total;
        double fadeIn = Math.min(1.5, effective / 3), fadeOut = Math.min(3.0, effective / 3);
        Path tmp = video.resolveSibling(video.getFileName() + ".audio.mp4");
        try {
            List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error", "-i", video.toString(),
                    "-ss", num(start), "-i", audio.toString(),
                    "-filter_complex", "[1:a]afade=t=in:d=" + num(fadeIn) + ",afade=t=out:st=" + num(effective - fadeOut) + ":d="
                            + num(fadeOut) + "[a]",
                    "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "128k"));
            if (total > 0) args.addAll(List.of("-t", num(total)));
            args.addAll(List.of("-movflags", "+faststart", tmp.toString()));
            ffmpeg.run("audio", args, TIMEOUT);
            Files.move(tmp, video, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.2f", Math.max(v, 0)); }
}
