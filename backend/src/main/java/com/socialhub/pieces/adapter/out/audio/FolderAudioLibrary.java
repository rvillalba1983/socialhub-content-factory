package com.socialhub.pieces.adapter.out.audio;

import com.socialhub.pieces.domain.AudioLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Busca el MP3 de un tema en una carpeta de audio (también en sus subcarpetas, hasta 3 niveles).
 * El nombre se compara sin tildes, mayúsculas ni signos: primero igual al del archivo (sin «.mp3» y sin un número inicial de
 * pista como «15 - ») y, si no, contenido en él; si así encajan varios, es un error en lugar de elegir uno al azar.
 */
@Component
public class FolderAudioLibrary implements AudioLibrary {

    private static final int DEPTH = 3;

    @Override
    public Path find(Path folder, String name) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("La «Carpeta de audio de Litoques» (" + folder + ") no está disponible en este "
                    + "ordenador. ¿Está abierto Google Drive para ordenador?");
        }
        String wanted = key(name);
        if (wanted.isEmpty()) throw new IllegalArgumentException("El nombre del tema no vale para buscar el MP3");
        List<Path> mp3;
        try (Stream<Path> walk = Files.walk(folder, DEPTH)) {
            mp3 = walk.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer la carpeta de audio de Litoques (" + folder + "): " + e.getMessage());
        }
        List<Path> exact = mp3.stream().filter(f -> key(stripTrackNumber(baseName(f))).equals(wanted) || key(baseName(f)).equals(wanted)).toList();
        List<Path> found = exact.isEmpty() ? mp3.stream().filter(f -> key(baseName(f)).contains(wanted)).toList() : exact;
        if (found.isEmpty()) {
            throw new IllegalArgumentException("No hay ningún MP3 del tema «" + name + "» en la carpeta de audio de Litoques ("
                    + folder + "). El archivo tiene que llevar el nombre del tema, por ejemplo «" + name + ".mp3»");
        }
        if (found.size() > 1) {
            throw new IllegalArgumentException("Hay varios MP3 que encajan con el tema «" + name + "» en la carpeta de audio de "
                    + "Litoques: " + String.join(", ", found.stream().map(f -> f.getFileName().toString()).toList())
                    + ". Deja uno o pon el nombre completo del archivo");
        }
        return found.getFirst();
    }

    private static String baseName(Path file) {
        String n = file.getFileName().toString();
        return n.substring(0, n.length() - 4);
    }

    /** Quita un número de pista al principio («15 - Púa», «03. Púa»). */
    private static String stripTrackNumber(String name) {
        return name.replaceFirst("^\\s*\\d{1,3}\\s*[-._)]*\\s*", "");
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
