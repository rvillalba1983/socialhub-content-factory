package com.socialhub.backup.adapter.out.files;

import com.socialhub.backup.domain.BackupFile;
import com.socialhub.backup.domain.BackupName;
import com.socialhub.backup.domain.BackupStore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
class FileBackupStore implements BackupStore {

    @Override
    public List<BackupFile> list(Path folder, boolean local) {
        if (!Files.isDirectory(folder)) return List.of();
        List<BackupFile> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            for (Path p : entries.toList()) {
                Optional<BackupName> name = BackupName.parse(p.getFileName().toString());
                if (name.isEmpty() || !Files.isRegularFile(p)) continue;
                files.add(new BackupFile(name.get().value(), name.get().createdAt(), Files.size(p), name.get().kind(), local));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la carpeta de copias " + folder, e);
        }
        return files;
    }

    @Override
    public void write(Path folder, String name, Writer writer) {
        Path target = folder.resolve(name);
        Path partial = folder.resolve(name + ".parcial");
        try {
            Files.createDirectories(folder);
            try (OutputStream out = Files.newOutputStream(partial)) {
                writer.writeTo(out);
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { /* se intentó */ }
            throw new UncheckedIOException("No se pudo guardar la copia en " + folder + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Path folder, String name) {
        if (BackupName.parse(name).isEmpty()) throw new IllegalArgumentException("No es una copia: " + name);
        try {
            Files.deleteIfExists(folder.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar la copia " + name, e);
        }
    }
}
