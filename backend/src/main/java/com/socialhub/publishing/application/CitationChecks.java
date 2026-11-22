package com.socialhub.publishing.application;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Citation;
import com.socialhub.publishing.domain.CitationMatcher;
import com.socialhub.publishing.domain.DocumentReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Comprueba que cada cita declarada por Claude aparece literalmente en un documento de las carpetas de la promoción.
 * Nunca da una cita por verificada si no pudo leer el documento: en ese caso avisa «sin verificar».
 */
@Component
@RequiredArgsConstructor
class CitationChecks {

    /** Resultado de una cita: {@code ok} si aparece en el documento; si no, el motivo para corregirla o comprobarla. */
    record Result(String key, boolean ok, String message) {}

    private static final int EXCERPT = 90;

    private final PromotionQueries promotions;
    private final DocumentReader reader;

    private record Cached(long size, long modified, DocumentReader.Reading reading) {}

    private final Map<Path, Cached> cache = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Path, Cached> eldest) { return size() > 16; }
    });

    List<Result> check(String promotionId, List<Citation> citations) {
        List<Path> roots = roots(promotionId);
        List<Result> results = new ArrayList<>();
        for (Citation c : citations) results.add(check(c, roots));
        return results;
    }

    private Result check(Citation c, List<Path> roots) {
        String key = "CITATION:" + HexFormat.of().formatHex(sha(CitationMatcher.normalize(c.text()) + "|" + c.sourceFile()), 0, 4);
        String quote = "«" + excerpt(c.text()) + "»";
        String file = c.sourceFile();
        String where = c.location() == null || c.location().isBlank() ? "ubicación no indicada" : c.location().strip();
        Path path;
        try {
            path = locate(file, roots);
        } catch (Outside e) {
            return new Result(key, false, "Cita sin verificar " + quote + ": «" + file + "» no está en las carpetas de la promoción. "
                    + "Pide a Claude otra cita de un documento de la promoción.");
        } catch (Missing e) {
            return new Result(key, false, "Cita no encontrada en el documento " + quote + ": no existe «" + file
                    + "» en las carpetas de la promoción (¿Drive sin sincronizar?).");
        }
        DocumentReader.Reading reading = read(path);
        if (!reading.readable()) {
            return new Result(key, false, "Sin verificar: comprueba la cita en " + path.getFileName() + ", " + where + " "
                    + quote + " (" + reading.reason() + ").");
        }
        OptionalInt part = CitationMatcher.find(reading.parts(), c.text());
        if (part.isEmpty()) {
            return new Result(key, false, "Cita no encontrada en el documento «" + path.getFileName() + "» " + quote
                    + ". Corrige la cita para que sea literal o pide a Claude otro fragmento.");
        }
        String found = "página".equals(reading.unit()) ? "página " + (part.getAsInt() + 1)
                : lineText(reading.parts().getFirst(), c.text());
        return new Result(key, true, "Cita verificada en «" + path.getFileName() + "», " + found + ".");
    }

    private static String lineText(String text, String citation) {
        int line = CitationMatcher.lineOf(text, citation);
        return line > 0 ? "línea " + line : "en el texto";
    }

    private static String excerpt(String text) {
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() <= EXCERPT ? t : t.substring(0, EXCERPT - 1) + "…";
    }

    private static byte[] sha(String text) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentReader.Reading read(Path path) {
        try {
            long size = Files.size(path);
            long modified = Files.getLastModifiedTime(path).toMillis();
            Cached cached = cache.get(path);
            if (cached != null && cached.size() == size && cached.modified() == modified) return cached.reading();
            DocumentReader.Reading reading = reader.read(path);
            cache.put(path, new Cached(size, modified, reading));
            return reading;
        } catch (IOException e) {
            return DocumentReader.Reading.unreadable("no se pudo leer el documento");
        }
    }

    private List<Path> roots(String promotionId) {
        List<Path> roots = new ArrayList<>();
        for (String folder : promotions.driveFolders(promotionId)) {
            try {
                Path p = Path.of(folder);
                if (Files.isDirectory(p)) roots.add(p.toRealPath());
            } catch (InvalidPathException | IOException e) {
                // carpeta sin sincronizar: no cuenta
            }
        }
        return roots;
    }

    private static final class Outside extends Exception {}

    private static final class Missing extends Exception {}

    /** El documento dentro de una carpeta de la promoción (siguiendo enlaces simbólicos, para que no se escape de ellas). */
    private static Path locate(String file, List<Path> roots) throws Outside, Missing {
        if (file.indexOf('\0') >= 0) throw new Missing();
        List<Path> candidates = new ArrayList<>();
        try {
            Path given = Path.of(file);
            if (given.isAbsolute()) candidates.add(given);
            else roots.forEach(r -> candidates.add(r.resolve(given)));
        } catch (InvalidPathException e) {
            throw new Missing();
        }
        boolean existsOutside = false;
        for (Path candidate : candidates) {
            try {
                if (!Files.isRegularFile(candidate)) continue;
                Path real = candidate.toRealPath();
                if (roots.stream().anyMatch(real::startsWith)) return real;
                existsOutside = true;
            } catch (IOException e) {
                // sigue con el siguiente
            }
        }
        if (existsOutside) throw new Outside();
        throw new Missing();
    }
}
