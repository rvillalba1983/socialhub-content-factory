package com.socialhub.backup;

import com.socialhub.armory.ArmoryService;
import com.socialhub.backup.domain.BackupFile;
import com.socialhub.backup.domain.BackupKind;
import com.socialhub.backup.domain.BackupName;
import com.socialhub.backup.domain.BackupRotation;
import com.socialhub.backup.domain.BackupStore;
import com.socialhub.backup.domain.DatabaseArchive;
import com.socialhub.backup.domain.SettingsRepository;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.DomainException;
import com.socialhub.platform.NotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Copias de la base de datos real. Van a la carpeta de copias elegida (un ajuste general); mientras no hay carpeta
 * elegida, o si no está disponible, van a la carpeta local {@code <datos>/copias} y sale un aviso en la armería.
 * En cada carpeta se guardan las {@value BackupRotation#KEEP} más recientes.
 */
@Slf4j
@Service
public class BackupService {

    static final String ARMORY_ID = "copias";
    static final String ARMORY_NAME = "Copias de seguridad";
    static final String ARMORY_DESCRIPTION = "Copia diaria de la base de datos real al abrir SocialHub, en la carpeta "
            + "de copias. Se guardan las " + BackupRotation.KEEP + " últimas.";

    public record Folders(@Nullable Path chosen, boolean chosenAvailable, Path local) {
        /** Dónde se guarda la próxima copia. */
        public Path target() { return chosen != null && chosenAvailable ? chosen : local; }
    }

    public record Restored(String restored, String safetyBackup, String haltedReason) {}

    private final BackupStore store;
    private final DatabaseArchive archive;
    private final SettingsRepository settings;
    private final ArmoryService armory;
    private final Clock clock;
    private final Path local;
    private final ReentrantLock lock = new ReentrantLock();

    BackupService(BackupStore store, DatabaseArchive archive, SettingsRepository settings, ArmoryService armory,
                  Clock clock, AppPaths paths) {
        this.store = store;
        this.archive = archive;
        this.settings = settings;
        this.armory = armory;
        this.clock = clock;
        this.local = paths.dataDir().resolve("copias");
    }

    public Folders folders() {
        Optional<Path> chosen = settings.get(SettingsRepository.BACKUP_FOLDER).map(Path::of);
        return new Folders(chosen.orElse(null), chosen.map(Files::isDirectory).orElse(false), local);
    }

    /** Todas las copias (la carpeta elegida y la local), de la más reciente a la más antigua. */
    public List<BackupFile> list() {
        Folders f = folders();
        List<BackupFile> all = new ArrayList<>(store.list(f.local(), true));
        if (f.chosen() != null && f.chosenAvailable() && !f.chosen().equals(f.local())) all.addAll(store.list(f.chosen(), false));
        all.sort(Comparator.comparing(BackupFile::createdAt).reversed());
        return all;
    }

    /** Elige la carpeta de copias (tiene que existir) o la quita con un texto vacío. */
    public Folders chooseFolder(@Nullable String folder) {
        if (folder == null || folder.isBlank()) {
            settings.delete(SettingsRepository.BACKUP_FOLDER);
        } else {
            Path path;
            try {
                path = Path.of(folder.trim()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                throw new DomainException("La carpeta no es válida: " + folder);
            }
            if (!Files.isDirectory(path)) throw new DomainException("La carpeta no existe: " + path);
            settings.put(SettingsRepository.BACKUP_FOLDER, path.toString());
        }
        report(null);
        return folders();
    }

    /** Hace una copia ahora y borra las que sobran en esa carpeta. */
    public BackupFile create(BackupKind kind) {
        lock.lock();
        try {
            Folders f = folders();
            Path target = f.target();
            String name = new BackupName(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), kind).value();
            try {
                store.write(target, name, archive::export);
            } catch (RuntimeException e) {
                report("No se pudo hacer la copia: " + e.getMessage());
                throw e;
            }
            boolean isLocal = target.equals(f.local());
            for (BackupFile old : BackupRotation.surplus(store.list(target, isLocal))) store.delete(target, old.name());
            log.info("Copia hecha: {} en {}", name, target);
            report(null);
            return store.list(target, isLocal).stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
        } finally {
            lock.unlock();
        }
    }

    /** La copia diaria: si hoy no hay ninguna copia (de ningún tipo, en ninguna carpeta), la hace. */
    public Optional<BackupFile> dailyIfNeeded() {
        if (AppModeHolder.haltedReason() != null) return Optional.empty();
        LocalDate today = LocalDate.now(clock);
        if (list().stream().anyMatch(b -> b.createdAt().toLocalDate().equals(today))) return Optional.empty();
        return Optional.of(create(BackupKind.DAILY));
    }

    /**
     * Restaura una copia: antes hace otra de lo actual («antes-de-restaurar») y después para SocialHub hasta que
     * Raúl lo cierre y lo vuelva a abrir.
     */
    public Restored restore(String name, String confirmation) {
        if (!name.equals(confirmation == null ? null : confirmation.trim())) {
            throw new DomainException("Para restaurar, escribe exactamente el nombre de la copia: " + name);
        }
        if (AppModeHolder.haltedReason() != null) throw new DomainException("SocialHub está parado: " + AppModeHolder.haltedReason());
        lock.lock();
        try {
            Path zip = locate(name);
            BackupFile safety = create(BackupKind.BEFORE_RESTORE);
            String reason = "Se ha restaurado la copia " + name + ". Cierra SocialHub y vuelve a abrirlo para seguir.";
            // Nada en segundo plano escribe mientras se cargan los datos
            AppModeHolder.halt("Restaurando la copia " + name + "…");
            try {
                archive.restore(zip);
            } catch (RuntimeException e) {
                // El cambio de datos es de una vez: si algo falla, los datos actuales siguen como estaban
                AppModeHolder.resume();
                throw e;
            }
            AppModeHolder.halt(reason);
            log.warn("Copia {} restaurada (copia previa: {}). SocialHub queda parado.", name, safety.name());
            return new Restored(name, safety.name(), reason);
        } finally {
            lock.unlock();
        }
    }

    private Path locate(String name) {
        if (BackupName.parse(name).isEmpty()) throw new NotFoundException("No existe la copia " + name);
        Folders f = folders();
        for (Path folder : f.chosen() != null && f.chosenAvailable() ? List.of(f.chosen(), f.local()) : List.of(f.local())) {
            Path p = folder.resolve(name);
            if (Files.isRegularFile(p)) return p;
        }
        throw new NotFoundException("No existe la copia " + name);
    }

    /** Estado de las copias en la armería (sale en «Necesita atención» si no está bien). */
    private void report(@Nullable String error) {
        Folders f = folders();
        String status, note;
        if (error != null) {
            status = "BLOCKED"; note = error;
        } else if (f.chosen() == null) {
            status = "BLOCKED"; note = "Las copias se guardan solo en este ordenador (" + f.local() + "). Elige una carpeta de Drive en «Copias».";
        } else if (!f.chosenAvailable()) {
            status = "BLOCKED"; note = "La carpeta de copias no está disponible (" + f.chosen() + "); se guardan en este ordenador. ¿Está abierto Google Drive?";
        } else {
            status = "OK"; note = "En " + f.chosen();
        }
        try {
            AppModeHolder.callIn(AppMode.REAL, () -> {
                armory.registerService(ARMORY_ID, ARMORY_NAME, ARMORY_DESCRIPTION, status, note, List.of("Copias de seguridad"));
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("No se pudo actualizar el estado de las copias en la armería: {}", e.toString());
        }
    }
}
