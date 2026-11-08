package com.socialhub.backup.domain;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Las copias guardadas en una carpeta. */
@SecondaryPort
public interface BackupStore {

    /** Las copias de la carpeta (solo los archivos con nombre de copia). Vacía si la carpeta no existe. */
    List<BackupFile> list(Path folder, boolean local);

    /** Escribe una copia. Mientras se escribe tiene otro nombre, así que nunca queda una copia a medias. */
    void write(Path folder, String name, Writer writer);

    void delete(Path folder, String name);

    @FunctionalInterface
    interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }
}
