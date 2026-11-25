package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.platform.process.SafeProcess;
import com.socialhub.publishing.domain.VideoInfo;
import com.socialhub.tools.adapter.out.ffmpeg.FfmpegTool;
import com.socialhub.tools.application.FfmpegService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ffprobe de verdad sobre vídeos de prueba: vertical, horizontal, solo audio y un archivo que no es vídeo. */
class VideoInspectionsTest {

    static FfmpegTool ffmpeg;
    static VideoInspections inspections;
    @TempDir static Path dir;

    @BeforeAll
    static void prepare() {
        // La misma carpeta que las pruebas de piezas: la librería recuerda dónde extrajo FFmpeg en esta JVM
        Path data = Path.of("target", "test-ffmpeg").toAbsolutePath();
        ffmpeg = new FfmpegTool(new SafeProcess(ExternalCalls.unobserved()),
                new AppPaths(new SocialHubProperties(data.toString(), null, null, null)));
        inspections = new VideoInspections(new FfmpegService(ffmpeg));
    }

    private static Path make(String name, String... args) throws Exception {
        Path file = dir.resolve(name);
        List<String> all = new ArrayList<>(List.of("-y", "-loglevel", "error"));
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

        assertThat(inspections.inspect(vertical)).contains(new VideoInfo(true, true, 1080, 1920));
        assertThat(inspections.inspect(horizontal)).contains(new VideoInfo(true, true, 1280, 720));
        assertThat(inspections.inspect(audioOnly)).contains(new VideoInfo(true, false, 0, 0));
        assertThat(inspections.inspect(broken)).contains(new VideoInfo(false, false, 0, 0));
        // Un archivo que no existe no se puede mirar: sin resultado, sin aviso
        assertThat(inspections.inspect(dir.resolve("no-esta.mp4"))).isEmpty();
    }

    @Test
    void swapsWidthAndHeightWhenTheVideoIsRotated() throws Exception {
        Path flat = make("plano.mp4", "-f", "lavfi", "-i", "testsrc=size=1920x1080:rate=10:duration=1", "-c:v", "libx264", "-pix_fmt", "yuv420p");
        Path rotated = make("girado.mp4", "-display_rotation", "90", "-i", flat.toString(), "-c", "copy");

        assertThat(inspections.inspect(flat)).contains(new VideoInfo(true, true, 1920, 1080));
        assertThat(inspections.inspect(rotated)).contains(new VideoInfo(true, true, 1080, 1920));
    }

    @Test
    void aTimeoutIsNotAnUnreadableVideoAndNothingIsCached() throws Exception {
        Path vertical = make("lento.mp4", "-f", "lavfi", "-i", "testsrc=size=1080x1920:rate=10:duration=1", "-c:v", "libx264", "-pix_fmt", "yuv420p");

        assertThat(ffmpeg.probeVideo(vertical, Duration.ofMillis(1))).isEmpty();
        assertThat(inspections.inspect(vertical)).contains(new VideoInfo(true, true, 1080, 1920));
    }
}
