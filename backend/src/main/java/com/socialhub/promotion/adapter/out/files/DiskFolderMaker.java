package com.socialhub.promotion.adapter.out.files;

import com.socialhub.promotion.domain.FolderMaker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
class DiskFolderMaker implements FolderMaker {

    @Override
    public boolean ensure(String location) {
        Path path = Path.of(location);
        if (Files.isDirectory(path)) return false;
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta " + location + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean reachable(String location) {
        try {
            Path path = Path.of(location);
            Path root = path.getRoot();
            if (root != null && !Files.isDirectory(root)) return false;
            return !Files.exists(path) || Files.isDirectory(path);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
