package com.socialhub.backup.domain;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Exportación e importación de la base de datos real en un .zip (un CSV por tabla y un manifest.json). */
@SecondaryPort
public interface DatabaseArchive {

    /** Exporta todas las tablas en una sola lectura coherente, sin parar nada. */
    void export(OutputStream out) throws IOException;

    /**
     * Sustituye los datos por los de la copia: recrea las tablas con las migraciones hasta la versión de la copia,
     * carga los datos, migra a la última versión y cambia unas tablas por otras de una vez.
     */
    void restore(Path zip);
}
