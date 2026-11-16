package com.socialhub.backup;

import com.socialhub.backup.domain.BackupFile;
import com.socialhub.backup.domain.BackupKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Copias de la base de datos real. Van a la carpeta de copias elegida (un ajuste general); mientras no hay carpeta
 * elegida, o si no está disponible, van a la carpeta local {@code <datos>/copias} y sale un aviso en la armería.
 */
@PrimaryPort
public interface Backups {

    /** Dónde se guardan las copias. */
    record Folders(@Nullable Path chosen, boolean chosenAvailable, Path local) {
        /** Dónde se guarda la próxima copia. */
        public Path target() { return chosen != null && chosenAvailable ? chosen : local; }
    }

    record Restored(String restored, String safetyBackup, String haltedReason) {}

    /** Hay carpeta de copias elegida (sin mirar el disco). */
    boolean hasChosenFolder();

    Folders folders();

    /** Todas las copias (la carpeta elegida y la local), de la más reciente a la más antigua. */
    List<BackupFile> list();

    /** Elige la carpeta de copias (tiene que existir) o la quita con un texto vacío. */
    Folders chooseFolder(@Nullable String folder);

    /** Hace una copia ahora y borra las que sobran en esa carpeta. */
    BackupFile create(BackupKind kind);

    /** La copia diaria: si hoy no hay ninguna copia (de ningún tipo, en ninguna carpeta), la hace. */
    Optional<BackupFile> dailyIfNeeded();

    /**
     * Restaura una copia: antes hace otra de lo actual («antes-de-restaurar») y después para SocialHub hasta que
     * Raúl lo cierre y lo vuelva a abrir.
     */
    Restored restore(String name, String confirmation);
}
