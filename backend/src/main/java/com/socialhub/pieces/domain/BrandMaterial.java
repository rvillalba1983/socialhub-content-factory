package com.socialhub.pieces.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * El material de una marca o promoción en las carpetas de Drive: la portada genérica de la marca y qué publicaciones
 * tienen imagen o vídeo propio. Solo lee; nunca escribe ni falla por una carpeta que no está.
 */
public final class BrandMaterial {

    /** Nombre (sin extensión) del archivo de portada de la marca, en la raíz de su carpeta Material. */
    public static final String COVER_NAME = "portada";
    private static final List<String> COVER_EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final int MAX_DEPTH = 6;
    private static final int MAX_FILES = 20_000;
    /** Palabras que no dicen de qué canción o tema es una publicación. */
    private static final Set<String> NOISE = Set.of("reel", "reels", "carrusel", "historia", "story", "stories", "post",
            "tiktok", "instagram", "video", "cancion", "tema", "clip", "lyric", "lyrics", "con", "del", "los", "las", "para",
            "por", "una", "uno", "que", "como", "sin");

    private BrandMaterial() {}

    /**
     * La portada de la marca: {@code portada.jpg}, {@code portada.jpeg} o {@code portada.png} (sin distinguir mayúsculas)
     * en la raíz de la carpeta, sin entrar en subcarpetas. Si hay varias, gana jpg, luego jpeg y luego png.
     */
    public static Optional<Path> cover(Path materialRoot) {
        if (!Files.isDirectory(materialRoot)) return Optional.empty();
        try (Stream<Path> list = Files.list(materialRoot)) {
            List<Path> found = list.filter(Files::isRegularFile)
                    .filter(BrandMaterial::isCover).toList();
            java.util.Comparator<Path> order = java.util.Comparator.<Path>comparingInt(
                    f -> COVER_EXTENSIONS.indexOf(PieceFiles.extension(f))).thenComparing(f -> f.getFileName().toString());
            return found.stream().min(order);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isCover(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return COVER_EXTENSIONS.stream().anyMatch(ext -> name.equals(COVER_NAME + "." + ext));
    }

    /** Lo que hay en las carpetas de material: sus imágenes y vídeos, con la ruta relativa normalizada. */
    public record Scan(boolean accessible, List<String> files) {

        /** Hay imagen o vídeo propio de ese título (canción o tema): todas sus palabras clave salen en la ruta de algún archivo. */
        public boolean covers(String title) {
            List<String> words = keywords(title);
            if (words.isEmpty()) return true; // sin palabras clave no se puede decidir: no se avisa
            return files.stream().anyMatch(f -> words.stream().allMatch(f::contains));
        }
    }

    /** Recorre las carpetas (como mucho 6 niveles y 20.000 archivos) buscando imágenes y vídeos. */
    public static Scan scan(List<Path> folders) {
        boolean accessible = false;
        List<String> files = new ArrayList<>();
        for (Path root : folders) {
            if (!Files.isDirectory(root)) continue;
            accessible = true;
            try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
                for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    if (files.size() >= MAX_FILES) break;
                    String ext = PieceFiles.extension(f);
                    if (!PieceFiles.IMAGES.contains(ext) && !PieceFiles.VIDEO.contains(ext)) continue;
                    if (root.relativize(f).getNameCount() == 1 && isCover(f)) continue; // la portada no es de ninguna canción
                    files.add(normalize(root.relativize(f).toString()));
                }
            } catch (IOException | RuntimeException e) {
                // Una carpeta que no se puede leer entera no impide mirar las demás
            }
        }
        return new Scan(accessible, List.copyOf(files));
    }

    /** Palabras de un título que sirven para reconocerlo en un nombre de archivo (sin tildes, de 3 letras o más). */
    static List<String> keywords(String title) {
        return java.util.Arrays.stream(normalize(title).split(" "))
                .filter(w -> w.length() >= 3 && !NOISE.contains(w)).distinct().toList();
    }

    /** Minúsculas, sin tildes y con todo lo que no es letra ni número convertido en un espacio. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
