package com.socialhub.publishing.adapter.out.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.platform.process.SafeProcess;
import com.socialhub.publishing.domain.VideoInspector;
import com.socialhub.tools.adapter.out.ffmpeg.FfmpegTool;
import com.socialhub.tools.application.FfmpegService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ffprobe de verdad sobre vídeos de prueba: vertical, horizontal, solo audio y un archivo que no es vídeo. */
class FfprobeVideoInspectorTest {

    static FfmpegTool ffmpeg;
    static FfprobeVideoInspector inspector;
    @TempDir static Path dir;

    @BeforeAll
    static void prepare() {
        // La misma carpeta que las pruebas de piezas: la librería recuerda dónde extrajo FFmpeg en esta JVM
        Path data = Path.of("target", "test-ffmpeg").toAbsolutePath();
        ffmpeg = new FfmpegTool(new SafeProcess(ExternalCalls.unobserved()),
                new AppPaths(new SocialHubProperties(data.toString(), null, null, null)));
        inspector = new FfprobeVideoInspector(new FfmpegService(ffmpeg));
    }

    private static Path make(String name, String... args) throws Exception {
        Path file = dir.resolve(name);
        List<String> all = new java.util.ArrayList<>(List.of("-y", "-loglevel", "error"));
        all.addAll(List.of(args));
        all.add(file.toString());
        ffmpeg.run("prueba", all, Duration.ofMinutes(2));
        return file;
    }

    @Test
    void readsTheSizeOfVerticalAndHorizontalVideosAndSpotsFilesWithoutImage() throws Exception {
        Path vertical = make("vertical.mp4", "-f", "lavfi", "-i", "testsrc=size=1080x1920:rate=10:duration=1", "-c:v", "libx264", "-pix_fmt", "yuv420p");
        Path horizontal = make("horizontal.mp4", "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=10:duration=1", "-c:v", "libx264", "-pix_fmt", "yuv420p");
        Path audioOnly = make("solo-audio.mp4", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "aac");
        Path broken = Files.writeString(dir.resolve("roto.mp4"), "esto no es un vídeo");

        assertThat(inspector.inspect(vertical)).contains(new VideoInspector.Info(true, true, 1080, 1920));
        assertThat(inspector.inspect(horizontal)).contains(new VideoInspector.Info(true, true, 1280, 720));
        assertThat(inspector.inspect(audioOnly)).contains(new VideoInspector.Info(true, false, 0, 0));
        assertThat(inspector.inspect(broken)).contains(new VideoInspector.Info(false, false, 0, 0));
        // Un archivo que no existe no se puede mirar: sin resultado, sin aviso
        assertThat(inspector.inspect(dir.resolve("no-esta.mp4"))).isEmpty();
    }
}
