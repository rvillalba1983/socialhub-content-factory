package com.socialhub.pieces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.settings.AudioSettings;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** El tema de Litoques sale de la carpeta de los ajustes; sin carpeta configurada, error claro en español. */
class SettingsLitoquesTracksTest {

    private static AudioSettings settings(Path folder) {
        return new AudioSettings() {
            @Override public Optional<Path> litoquesFolder() { return Optional.ofNullable(folder); }
            @Override public View view() { return new View(folder == null ? null : folder.toString(), folder != null); }
            @Override public View update(String f) { throw new UnsupportedOperationException(); }
        };
    }

    @Test
    void usesTheFolderOfTheSettings() {
        Path folder = Path.of("/audio");
        var tracks = new SettingsLitoquesTracks(settings(folder), (where, name) -> where.resolve(name + ".mp3"));
        assertThat(tracks.track("Púa")).isEqualTo(folder.resolve("Púa.mp3"));
    }

    @Test
    void withoutAFolderItExplainsWhatIsMissing() {
        var tracks = new SettingsLitoquesTracks(settings(null), (where, name) -> where.resolve(name));
        assertThatThrownBy(() -> tracks.track("Púa")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay «Carpeta de audio de Litoques» en los ajustes").hasMessageContaining("«Púa»");
    }
}
