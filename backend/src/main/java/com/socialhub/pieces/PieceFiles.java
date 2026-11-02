package com.socialhub.pieces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Acceso seguro a los archivos de una promoción:
 * <ul>
 *   <li>Solo dentro de sus carpetas (recursos de tipo «Carpeta»), comprobando la ruta real para que ni {@code ..} ni
 *       los enlaces simbólicos lleven fuera.</li>
 *   <li>Solo formatos conocidos y con tamaño máximo.</li>
 *   <li>Nunca sobrescribe: si el archivo existe, prueba {@code nombre_2}, {@code nombre_3}…</li>
 * </ul>
 */
final class PieceFiles {

    static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png");
    static final Set<String> AUDIO = Set.of("mp3", "m4a", "wav", "aac", "ogg");
    static final Set<String> VIDEO = Set.of("mp4", "mov", "m4v", "mkv", "webm");
    private static final long MB = 1024L * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[\\p{L}\\p{N} _.-]{1,80}$");

    private final List<Path> roots;
    private final Path base;

    /**
     * @param folders carpetas de la promoción
     * @param base carpeta del spec: las rutas relativas parten de aquí
     */
    PieceFiles(List<String> folders, Path base) {
        List<Path> real = new ArrayList<>();
        for (String folder : folders) {
            try {
                real.add(Path.of(folder).toRealPath());
            } catch (IOException | RuntimeException e) {
                // Una carpeta que no existe en este ordenador no da permiso a nada
            }
        }
        if (real.isEmpty()) {
            throw new IllegalArgumentException("La promoción no tiene ninguna carpeta accesible en este ordenador. "
                    + "Añádela en «Recursos» como Carpeta.");
        }
        this.roots = List.copyOf(real);
        this.base = inside(base, true);
    }

    List<Path> roots() { return roots; }

    /** Archivo de entrada existente, del tipo indicado y con tamaño máximo. */
    Path input(String path, Set<String> types, long maxMb) {
        Path resolved = inside(resolve(path), true);
        if (!Files.isRegularFile(resolved)) throw new IllegalArgumentException("No es un archivo: " + path);
        String ext = extension(resolved);
        if (!types.contains(ext)) throw new IllegalArgumentException("Formato no admitido (" + ext + "): " + path);
        try {
            if (Files.size(resolved) > maxMb * MB) {
                throw new IllegalArgumentException("Demasiado grande (más de " + maxMb + " MB): " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer " + path);
        }
        return resolved;
    }

    /** Comprueba, sin crear nada, que la carpeta de salida queda dentro de las carpetas de la promoción. */
    void checkOutputDir(String path) {
        insideLexically(resolve(path).toAbsolutePath().normalize());
    }

    /** Carpeta de salida, que se crea si no existe. */
    Path outputDir(String path) throws IOException {
        Path dir = resolve(path).toAbsolutePath().normalize();
        insideLexically(dir);
        Files.createDirectories(dir);
        return inside(dir, true);
    }

    /** Archivo nuevo en la carpeta de salida. Si ya existe uno con ese nombre, se numera. */
    static Path fresh(Path dir, String name, String extension) {
        Path candidate = dir.resolve(name + "." + extension);
        for (int n = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); n++) {
            candidate = dir.resolve(name + "_" + n + "." + extension);
        }
        return candidate;
    }

    static String safeName(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de pieza no válido: «" + name + "». Solo letras, números, espacios, _ . -");
        }
        return name;
    }

    private Path resolve(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : base.resolve(p);
    }

    private Path inside(Path path, boolean mustExist) {
        Path real;
        try {
            real = mustExist ? path.toRealPath() : path.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalArgumentException("No existe: " + path);
        }
        insideLexically(real);
        return real;
    }

    private void insideLexically(Path path) {
        if (roots.stream().noneMatch(path::startsWith)) {
            throw new IllegalArgumentException("Fuera de las carpetas de la promoción: " + path);
        }
    }

    static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
