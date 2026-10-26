package com.socialhub.platform.postgres;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Localiza los programas de PostgreSQL (initdb, pg_ctl, postgres).
 *
 * <p>Orden: carpeta configurada (la del instalador) o, si no hay, los binarios incluidos en la
 * aplicación, que se descomprimen una sola vez.
 */
public final class PostgresBinaries {

    private static final Logger log = LoggerFactory.getLogger(PostgresBinaries.class);
    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Path binDir;

    private PostgresBinaries(Path binDir) {
        this.binDir = binDir;
    }

    public static PostgresBinaries resolve(String configuredDir, Path runtimeDir) {
        if (configuredDir != null && !configuredDir.isBlank()) {
            Path bin = Path.of(configuredDir).resolve("bin");
            PostgresBinaries binaries = new PostgresBinaries(bin);
            binaries.check();
            return binaries;
        }
        return extractBundled(runtimeDir);
    }

    public Path initdb() { return exe("initdb"); }
    public Path pgCtl() { return exe("pg_ctl"); }
    public Path postgres() { return exe("postgres"); }

    private Path exe(String name) {
        return binDir.resolve(WINDOWS ? name + ".exe" : name);
    }

    private void check() {
        for (Path p : new Path[] {initdb(), pgCtl(), postgres()}) {
            if (!Files.isRegularFile(p)) {
                throw new IllegalStateException("Falta " + p + ". La instalación de PostgreSQL está incompleta.");
            }
        }
    }

    private static PostgresBinaries extractBundled(Path runtimeDir) {
        Resource archive = findArchive();
        String name = archive.getFilename() == null ? "postgres" : archive.getFilename().replace(".txz", "");
        Path target = runtimeDir.resolve(name);
        Path marker = target.resolve(".complete");
        if (!Files.exists(marker)) {
            log.info("Descomprimiendo PostgreSQL incluido en {}", target);
            try {
                Path tmp = Files.createTempDirectory(ensure(runtimeDir), name + "-");
                untar(archive, tmp);
                deleteRecursively(target);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                Files.writeString(marker, "ok");
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo descomprimir PostgreSQL", e);
            }
        }
        PostgresBinaries binaries = new PostgresBinaries(target.resolve("bin"));
        binaries.check();
        return binaries;
    }

    private static Path ensure(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    private static Resource findArchive() {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath*:postgres-*.txz");
            if (found.length == 0) {
                throw new IllegalStateException(
                        "No hay binarios de PostgreSQL incluidos para este sistema. Configura socialhub.postgres.binaries-dir.");
            }
            String os = WINDOWS ? "windows" : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "darwin" : "linux";
            return Arrays.stream(found)
                    .filter(r -> r.getFilename() != null && r.getFilename().contains(os))
                    .findFirst()
                    .orElse(found[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void untar(Resource archive, Path target) throws IOException {
        try (InputStream raw = new BufferedInputStream(archive.getInputStream());
             XZCompressorInputStream xz = new XZCompressorInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Entrada sospechosa en el archivo: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else if (entry.isSymbolicLink()) {
                    Files.createDirectories(out.getParent());
                    Path link = Path.of(entry.getLinkName());
                    if (!out.getParent().resolve(link).normalize().startsWith(target)) {
                        throw new IOException("Enlace sospechoso en el archivo: " + entry.getName());
                    }
                    Files.deleteIfExists(out);
                    Files.createSymbolicLink(out, link);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                    if (!WINDOWS) {
                        Files.setPosixFilePermissions(out, permissions(entry.getMode()));
                    }
                }
            }
        }
    }

    private static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        return perms;
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
