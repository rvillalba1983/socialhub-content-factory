package com.socialhub.pieces.application;

import com.socialhub.pieces.domain.AudioLibrary;
import com.socialhub.settings.AudioSettings;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Los temas de Litoques de la «Carpeta de audio de Litoques» de los ajustes. */
@Component
@RequiredArgsConstructor
class SettingsLitoquesTracks implements LitoquesTracks {

    private final AudioSettings settings;
    private final AudioLibrary library;

    @Override
    public Path track(String name) {
        Path folder = settings.litoquesFolder().orElseThrow(() -> new IllegalArgumentException(
                "No hay «Carpeta de audio de Litoques» en los ajustes, así que no se puede mezclar el tema «" + name + "»"));
        return library.find(folder, name);
    }
}
