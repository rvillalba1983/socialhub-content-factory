package com.socialhub.pieces.application;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.platform.process.SafeProcess;
import com.socialhub.tools.adapter.out.ffmpeg.FfmpegTool;
import java.nio.file.Path;

/**
 * Un único FFmpeg para todas las pruebas de piezas de la misma JVM: la carpeta donde se extrae se fija la primera vez (la
 * librería la recuerda), así que no puede ser una carpeta temporal de una sola clase de prueba.
 */
final class SharedFfmpeg {

    private static FfmpegTool tool;

    private SharedFfmpeg() {}

    static synchronized FfmpegTool get() {
        if (tool == null) {
            Path data = Path.of("target", "test-ffmpeg").toAbsolutePath();
            tool = new FfmpegTool(new SafeProcess(ExternalCalls.unobserved()),
                    new AppPaths(new SocialHubProperties(data.toString(), null, null, null)));
        }
        return tool;
    }
}
