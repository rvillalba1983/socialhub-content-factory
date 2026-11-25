package com.socialhub.pieces.adapter.out.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.settings.AudioSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La búsqueda del MP3 de un tema en la «Carpeta de audio de Litoques». */
class FolderAudioLibraryTest {

    @TempDir Path folder;

    private static AudioSettings settings(Path path) {
        return new AudioSettings() {
            @Override public Optional<Path> litoquesFolder() { return Optional.ofNullable(path); }
            @Override public View view() { return new View(path == null ? null : path.toString(), path != null && Files.isDirectory(path)); }
            @Override public View update(String f) { throw new UnsupportedOperationException(); }
        };
    }

    private Path mp3(String relative) throws Exception {
        Path file = folder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
        return file;
    }

    @Test
    void findsTheTrackByNameIgnoringCaseAccentsAndTrackNumber() throws Exception {
        Path pua = mp3("15 - Púa.mp3");
        mp3("16 - Otra canción.mp3");
        mp3("notas.txt");
        FolderAudioLibrary library = new FolderAudioLibrary(settings(folder));
        assertThat(library.track("pua")).isEqualTo(pua);
        assertThat(library.track("PÚA")).isEqualTo(pua);
        assertThat(library.track("Otra cancion")).isEqualTo(folder.resolve("16 - Otra canción.mp3"));
    }

    @Test
    void looksInSubfoldersAndPrefersAnExactNameOverAPartialOne() throws Exception {
        Path exact = mp3("Disco/Púa.mp3");
        mp3("Disco/Púa (instrumental).mp3");
        assertThat(new FolderAudioLibrary(settings(folder)).track("Púa")).isEqualTo(exact);
    }

    @Test
    void explainsInSpanishWhatIsWrong() throws Exception {
        mp3("Púa (directo).mp3");
        mp3("Púa (estudio).mp3");
        assertThatThrownBy(() -> new FolderAudioLibrary(settings(null)).track("Púa"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No hay «Carpeta de audio de Litoques»");
        assertThatThrownBy(() -> new FolderAudioLibrary(settings(folder.resolve("no-esta"))).track("Púa"))
                .hasMessageContaining("no está disponible");
        assertThatThrownBy(() -> new FolderAudioLibrary(settings(folder)).track("Inexistente"))
                .hasMessageContaining("No hay ningún MP3 del tema «Inexistente»");
        assertThatThrownBy(() -> new FolderAudioLibrary(settings(folder)).track("Púa"))
                .hasMessageContaining("Hay varios MP3").hasMessageContaining("Púa (directo).mp3");
    }
}
