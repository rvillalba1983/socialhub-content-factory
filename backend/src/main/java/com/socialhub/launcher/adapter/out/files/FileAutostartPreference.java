package com.socialhub.launcher.adapter.out.files;

import com.socialhub.launcher.domain.AutostartPreference;
import com.socialhub.platform.AppPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@code <datos>/arranque-con-sistema.txt}: «si» o «no». */
@Component
class FileAutostartPreference implements AutostartPreference {

    private final Path file;

    FileAutostartPreference(AppPaths paths) { this.file = paths.dataDir().resolve("arranque-con-sistema.txt"); }

    @Override
    public Optional<Boolean> get() {
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            return switch (Files.readString(file).strip().toLowerCase(java.util.Locale.ROOT)) {
                case "si" -> Optional.of(true);
                case "no" -> Optional.of(false);
                default -> Optional.empty();
            };
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void set(boolean enabled) {
        try {
            Files.writeString(file, enabled ? "si" : "no");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
