package com.socialhub.pieces.adapter.out.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La búsqueda del MP3 de un tema en la «Carpeta de audio de Litoques». */
class FolderAudioLibraryTest {

    @TempDir Path folder;

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
        FolderAudioLibrary library = new FolderAudioLibrary();
        assertThat(library.find(folder, "pua")).isEqualTo(pua);
        assertThat(library.find(folder, "PÚA")).isEqualTo(pua);
        assertThat(library.find(folder, "Otra cancion")).isEqualTo(folder.resolve("16 - Otra canción.mp3"));
    }

    @Test
    void looksInSubfoldersAndPrefersAnExactNameOverAPartialOne() throws Exception {
        Path exact = mp3("Disco/Púa.mp3");
        mp3("Disco/Púa (instrumental).mp3");
        assertThat(new FolderAudioLibrary().find(folder, "Púa")).isEqualTo(exact);
    }

    @Test
    void explainsInSpanishWhatIsWrong() throws Exception {
        mp3("Púa (directo).mp3");
        mp3("Púa (estudio).mp3");
        assertThatThrownBy(() -> new FolderAudioLibrary().find(folder.resolve("no-esta"), "Púa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está disponible");
        assertThatThrownBy(() -> new FolderAudioLibrary().find(folder, "Inexistente"))
                .hasMessageContaining("No hay ningún MP3 del tema «Inexistente»");
        assertThatThrownBy(() -> new FolderAudioLibrary().find(folder, "Púa"))
                .hasMessageContaining("Hay varios MP3").hasMessageContaining("Púa (directo).mp3");
    }
}
