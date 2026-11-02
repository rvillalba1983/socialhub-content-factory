package com.socialhub.publishing;

import com.socialhub.promotion.BrandIdentity;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Comprobaciones deterministas de una publicación: texto, identidad de la marca, duplicados y archivos.
 * Los límites salen de la documentación de cada plataforma (ver {@code docs/fase-6.md}).
 * Cada aviso tiene una clave estable para poder aceptarlo.
 */
final class QualityChecker {

    static final int CAPTION_MAX = 2200;
    static final int INSTAGRAM_HASHTAGS_MAX = 30;
    static final long MB = 1024L * 1024;
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEOS = Set.of("mp4", "mov", "m4v", "webm");
    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");

    enum Kind { POST, CAROUSEL, STORY, REEL, VIDEO }

    private QualityChecker() {}

    /** Tipo de pieza a partir del tipo libre de la publicación («Carrusel», «Historia», «Reel»…). */
    static Kind kind(String type) {
        String t = normalize(type);
        if (t.contains("carrusel") || t.contains("carousel")) return Kind.CAROUSEL;
        if (t.contains("historia") || t.contains("story") || t.contains("stories")) return Kind.STORY;
        if (t.contains("reel")) return Kind.REEL;
        if (t.contains("video") || t.contains("clip")) return Kind.VIDEO;
        return Kind.POST;
    }

    /**
     * @param others textos de las demás publicaciones de la promoción (id → texto), para detectar duplicados
     * @return clave → mensaje, en orden
     */
    static Map<String, String> check(Publication p, List<String> files, BrandIdentity identity, Map<String, String> others) {
        Map<String, String> issues = new LinkedHashMap<>();
        Kind kind = kind(p.getType());
        String caption = p.getCaption() == null ? "" : p.getCaption();
        String hashtags = p.getHashtags() == null ? "" : p.getHashtags();
        String all = caption + "\n" + hashtags;

        // ---- Texto
        if (kind != Kind.STORY) {
            if (caption.isBlank()) issues.put("TEXT_MISSING", "Falta el texto de la publicación.");
            int length = caption.length() + (hashtags.isBlank() ? 0 : 2 + hashtags.length());
            if (length > CAPTION_MAX) {
                issues.put("TEXT_TOO_LONG", "El texto con los hashtags tiene " + length + " caracteres: el máximo es " + CAPTION_MAX + ".");
            }
            if (p.getPlatform() == Platform.INSTAGRAM) {
                long tags = HASHTAG.matcher(all).results().map(m -> m.group().toLowerCase(Locale.ROOT)).distinct().count();
                if (tags > INSTAGRAM_HASHTAGS_MAX) {
                    issues.put("HASHTAGS_TOO_MANY", "Lleva " + tags + " hashtags: Instagram admite " + INSTAGRAM_HASHTAGS_MAX + ".");
                }
            }
            if (!identity.callToAction().isEmpty() && !normalize(caption).contains(normalize(identity.callToAction()))) {
                issues.put("CTA_MISSING", "Falta la llamada a la acción de la marca: «" + identity.callToAction() + "».");
            }
            Matcher fixed = HASHTAG.matcher(identity.fixedHashtags());
            String lower = all.toLowerCase(Locale.ROOT);
            while (fixed.find()) {
                String tag = fixed.group().toLowerCase(Locale.ROOT);
                if (!Pattern.compile(Pattern.quote(tag) + "(?![\\p{L}\\p{N}_])").matcher(lower).find()) {
                    issues.put("HASHTAG_MISSING:" + tag, "Falta el hashtag fijo de la marca " + tag + ".");
                }
            }
        }
        String text = normalize(all + "\n" + p.getTitle());
        for (String forbidden : identity.forbiddenExpressions()) {
            if (!normalize(forbidden).isBlank() && text.contains(normalize(forbidden))) {
                issues.put("FORBIDDEN:" + forbidden.toLowerCase(Locale.ROOT), "Lleva una expresión prohibida por la marca: «" + forbidden + "».");
            }
        }
        if (!caption.isBlank()) {
            String mine = normalize(caption).replaceAll("\\s+", " ").strip();
            others.forEach((id, other) -> {
                if (other != null && !other.isBlank() && normalize(other).replaceAll("\\s+", " ").strip().equals(mine)) {
                    issues.put("DUPLICATE:" + id, "El texto es igual al de la publicación " + id + ".");
                }
            });
        }

        // ---- Archivos
        if (files.isEmpty()) {
            issues.put("FILES_MISSING", "No tiene archivos asociados.");
            return issues;
        }
        int images = 0;
        int videos = 0;
        for (String file : files) {
            Path path;
            try {
                path = Path.of(file);
            } catch (InvalidPathException e) {
                issues.put("FILE_MISSING:" + file, "Ruta no válida: " + file);
                continue;
            }
            if (!Files.isRegularFile(path)) {
                issues.put("FILE_MISSING:" + file, "No se encuentra el archivo " + file + " (¿Drive sin sincronizar?).");
                continue;
            }
            String ext = extension(path);
            boolean image = IMAGES.contains(ext);
            boolean video = VIDEOS.contains(ext);
            if (image) images++;
            if (video) videos++;
            String format = formatProblem(p.getPlatform(), kind, ext, image, video);
            if (format != null) issues.put("FILE_FORMAT:" + file, format + ": " + path.getFileName());
            long max = maxBytes(p.getPlatform(), kind, video);
            try {
                long size = Files.size(path);
                if (size > max) {
                    issues.put("FILE_SIZE:" + file, path.getFileName() + " pesa " + (size / MB) + " MB: el máximo es " + (max / MB) + " MB.");
                }
            } catch (IOException e) {
                issues.put("FILE_MISSING:" + file, "No se puede leer " + file);
            }
            if (image) {
                String ratio = ratioProblem(p.getPlatform(), kind, path);
                if (ratio != null) issues.put("IMAGE_RATIO:" + file, ratio + ": " + path.getFileName());
            }
        }
        if (kind == Kind.CAROUSEL) {
            int max = p.getPlatform() == Platform.INSTAGRAM ? 20 : 35;
            if (files.size() > max) issues.put("CAROUSEL_COUNT", "El carrusel tiene " + files.size() + " elementos: el máximo es " + max + ".");
            if (files.size() < 2) issues.put("CAROUSEL_COUNT", "Un carrusel necesita al menos 2 elementos.");
            if (p.getPlatform() == Platform.TIKTOK && videos > 0) {
                issues.put("CAROUSEL_MIX", "TikTok no admite mezclar fotos y vídeos en la misma publicación.");
            }
        }
        if ((kind == Kind.REEL || kind == Kind.VIDEO) && videos == 0) {
            issues.put("VIDEO_MISSING", "Un " + (kind == Kind.REEL ? "reel" : "vídeo") + " necesita un archivo de vídeo.");
        }
        return issues;
    }

    private static @Nullable String formatProblem(Platform platform, Kind kind, String ext, boolean image, boolean video) {
        if (!image && !video) return "Formato no admitido (" + ext + ")";
        if ((kind == Kind.REEL || kind == Kind.VIDEO) && !video) return null; // la portada puede ser una imagen
        if (platform == Platform.INSTAGRAM) {
            if (image && !ext.equals("jpg") && !ext.equals("jpeg")) return "Instagram solo admite imágenes JPEG";
            if (video && !ext.equals("mp4") && !ext.equals("mov")) return "Instagram solo admite vídeos MP4 o MOV";
        } else if (video && !Set.of("mp4", "mov", "webm").contains(ext)) {
            return "TikTok solo admite vídeos MP4, MOV o WebM";
        }
        return null;
    }

    private static long maxBytes(Platform platform, Kind kind, boolean video) {
        if (platform == Platform.INSTAGRAM) {
            if (!video) return 8 * MB;
            return kind == Kind.STORY ? 100 * MB : 300 * MB;
        }
        return video ? 4096 * MB : 20 * MB;
    }

    private static @Nullable String ratioProblem(Platform platform, Kind kind, Path path) {
        int[] size = dimensions(path);
        if (size == null) return "No se pueden leer las dimensiones";
        double ratio = (double) size[0] / size[1];
        String dims = size[0] + "×" + size[1];
        boolean vertical = Math.abs(ratio - 9.0 / 16) <= 0.02;
        if (platform == Platform.INSTAGRAM && (kind == Kind.POST || kind == Kind.CAROUSEL)) {
            if (ratio < 0.8 - 0.01 || ratio > 1.91 + 0.01) return "Proporción " + dims + " fuera de lo que admite Instagram (de 4:5 a 1,91:1)";
        } else if (kind == Kind.STORY || platform == Platform.TIKTOK) {
            if (!vertical) return "Proporción " + dims + ": lo recomendado es vertical 9:16 (se recortará)";
        }
        return null;
    }

    /** Ancho y alto leyendo solo la cabecera de la imagen. */
    static int @Nullable [] dimensions(Path path) {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) return null;
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
