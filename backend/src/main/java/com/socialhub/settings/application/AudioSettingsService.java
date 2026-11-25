package com.socialhub.settings.application;

import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.settings.AudioSettings;
import com.socialhub.settings.domain.SettingStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AudioSettingsService implements AudioSettings {

    static final int MAX_LENGTH = 500;

    private final SettingStore store;
    private final ApplicationEventPublisher events;

    @Override
    public Optional<Path> litoquesFolder() {
        return store.get(SettingStore.LITOQUES_AUDIO_FOLDER).filter(v -> !v.isBlank()).flatMap(v -> {
            try {
                return Optional.of(Path.of(v.strip()));
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public View view() {
        Optional<Path> folder = litoquesFolder();
        return new View(folder.map(Path::toString).orElse(null), folder.map(Files::isDirectory).orElse(false));
    }

    @Override
    public View update(@Nullable String folder) {
        String value = folder == null ? "" : folder.strip();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La ruta de la carpeta de audio supera los " + MAX_LENGTH + " caracteres");
        }
        if (!value.isEmpty()) {
            Path path;
            try {
                path = Path.of(value);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("La ruta de la carpeta de audio no es válida: " + value);
            }
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("La carpeta de audio de Litoques tiene que ser una ruta completa (por ejemplo "
                        + "G:\\Mi unidad\\Litoques\\Audio), no «" + value + "»");
            }
        }
        store.put(SettingStore.LITOQUES_AUDIO_FOLDER, value);
        events.publishEvent(new FactoryChanged("settings", null));
        return view();
    }
}
