package com.socialhub.updates.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Una versión publicada: su número (sin la «v»), su página y sus archivos. */
public record Release(String version, String pageUrl, List<Asset> assets) {

    public record Asset(String name, String url) {}

    /** El instalador de este sistema (.msi en Windows, .dmg en macOS), si la release lo trae. */
    public Optional<String> installerFor(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String ext = os.contains("win") ? ".msi" : os.contains("mac") ? ".dmg" : null;
        if (ext == null) return Optional.empty();
        return assets.stream().filter(a -> a.name().toLowerCase(Locale.ROOT).endsWith(ext)).map(Asset::url).findFirst();
    }
}
