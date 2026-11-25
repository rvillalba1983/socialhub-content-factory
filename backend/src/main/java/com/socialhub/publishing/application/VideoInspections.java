package com.socialhub.publishing.application;

import com.socialhub.publishing.domain.VideoInfo;
import com.socialhub.tools.Ffmpeg;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mira los vídeos con ffprobe (el FFmpeg incluido). El resultado se recuerda mientras el archivo no cambie (ruta, tamaño y
 * fecha), porque los avisos de calidad se calculan en cada consulta de la ficha. Vacío si no se puede mirar (sin ffprobe):
 * entonces no se avisa de lo que no se sabe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class VideoInspections {

    private static final int MAX_CACHED = 500;

    private record Key(Path file, long size, long modified) {}

    private final Ffmpeg ffmpeg;
    private final ConcurrentHashMap<Key, VideoInfo> cache = new ConcurrentHashMap<>();

    Optional<VideoInfo> inspect(Path file) {
        Key key;
        try {
            key = new Key(file.toAbsolutePath().normalize(), Files.size(file), Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            return Optional.empty();
        }
        VideoInfo known = cache.get(key);
        if (known != null) return Optional.of(known);
        try {
            Ffmpeg.VideoInfo seen = ffmpeg.probeVideo(file);
            VideoInfo info = new VideoInfo(seen.readable(), seen.hasVideo(), seen.width(), seen.height());
            if (cache.size() >= MAX_CACHED) cache.clear();
            cache.put(key, info);
            return Optional.of(info);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | LinkageError e) {
            log.warn("No se pudo mirar el vídeo {}: {}", file.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
