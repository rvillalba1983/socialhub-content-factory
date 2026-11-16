package com.socialhub.tools.application;

import com.socialhub.platform.process.SafeProcess;
import com.socialhub.tools.Ffmpeg;
import com.socialhub.tools.domain.VideoProcessor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FfmpegService implements Ffmpeg {

    private final VideoProcessor processor;

    @Override
    public SafeProcess.Result run(String operation, List<String> arguments, Duration timeout) throws InterruptedException {
        return processor.run(operation, arguments, timeout);
    }

    @Override
    public double probeDuration(Path media) throws InterruptedException {
        return processor.probeDuration(media);
    }
}
