package com.socialhub.publishing.adapter.out.keyfile;

import com.socialhub.platform.AppPaths;
import com.socialhub.publishing.domain.SecretStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Cada clave en {@code <datos>/secretos/<nombre>.key}, legible solo por el usuario que ejecuta SocialHub
 * (permisos 600 en macOS y Linux; en Windows, una ACL con solo el propietario). La carpeta de datos no se sube a
 * Drive ni al repositorio.
 */
@Component
class FileSecretStore implements SecretStore {

    private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,40}");

    private final Path dir;

    FileSecretStore(AppPaths paths) {
        this.dir = paths.dataDir().resolve("secretos");
    }

    @Override
    public Optional<String> get(String name) {
        Path file = file(name);
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            String value = Files.readString(file, StandardCharsets.UTF_8).strip();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer la clave " + name, e);
        }
    }

    @Override
    public void put(String name, String value) {
        Path file = file(name);
        try {
            Files.createDirectories(dir);
            ownerOnly(dir, true);
            Path partial = dir.resolve("." + name + ".part");
            Files.deleteIfExists(partial);
            Files.createFile(partial);
            ownerOnly(partial, false);
            Files.writeString(partial, value.strip(), StandardCharsets.UTF_8);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            ownerOnly(file, false);
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede guardar la clave " + name, e);
        }
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede borrar la clave " + name, e);
        }
    }

    private Path file(String name) {
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Nombre de clave no válido: " + name);
        return dir.resolve(name + ".key");
    }

    /** Solo el propietario lee y escribe. */
    static void ownerOnly(Path path, boolean directory) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            UserPrincipal owner = acl.getOwner();
            AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            acl.setAcl(List.of(entry));
        }
    }
}
