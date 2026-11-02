package com.socialhub.tools;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.process.SafeProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * FFmpeg incluido en la aplicación (librería bytedeco, variante GPL con H.264). El ejecutable se descomprime la
 * primera vez en la carpeta de datos ({@code runtime/javacpp}).
 */
@Slf4j
@Component
public class FfmpegTool implements LocalTool {

    private final SafeProcess process;
    private final Path cacheDir;
    private volatile @Nullable Path executable;

    public FfmpegTool(SafeProcess process, AppPaths paths) {
        this.process = process;
        this.cacheDir = paths.runtime().resolve("javacpp");
    }

    @Override public String id() { return "ffmpeg"; }
    @Override public String name() { return "FFmpeg"; }
    @Override public String description() { return "Vídeo: recorte, compresión, montaje y audio"; }
    @Override public String license() { return "GPL (incluido con la aplicación)"; }
    @Override public List<String> capabilities() { return List.of("reel", "clip", "comprimir"); }

    /** Ruta del ejecutable incluido. */
    public synchronized Path executable() {
        if (executable == null) {
            System.setProperty("org.bytedeco.javacpp.cachedir", cacheDir.toString());
            executable = Path.of(Loader.load(org.bytedeco.ffmpeg.ffmpeg.class));
            log.info("FFmpeg incluido en {}", executable);
        }
        return executable;
    }

    /** Ejecuta FFmpeg con argumentos ya validados por quien lo llama. */
    public SafeProcess.Result run(String operation, List<String> arguments, Duration timeout) throws InterruptedException {
        return process.run("ffmpeg", operation, executable(), arguments, null, timeout);
    }

    @Override
    public Check check() throws InterruptedException {
        try {
            String output = run("version", List.of("-hide_banner", "-version"), Duration.ofSeconds(30)).output();
            String first = output.lines().findFirst().orElse("FFmpeg");
            boolean h264 = output.contains("--enable-libx264");
            return new Check(h264, h264 ? first : first + " · sin H.264");
        } catch (InterruptedException e) {
            throw e;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return new Check(false, "No funciona: " + e.getMessage());
        }
    }
}
