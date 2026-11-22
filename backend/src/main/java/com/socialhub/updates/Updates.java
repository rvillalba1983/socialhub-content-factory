package com.socialhub.updates;

import java.time.Instant;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Qué versión hay instalada y si hay otra más nueva publicada. */
@PrimaryPort
public interface Updates {

    enum State { UP_TO_DATE, UPDATE_AVAILABLE, UNKNOWN }

    /**
     * @param installedVersion la versión en marcha
     * @param latestVersion la última publicada, si se sabe
     * @param installerUrl enlace directo al instalador de este sistema, si la release lo trae
     * @param releaseUrl página de la release
     * @param checkedAt cuándo se miró GitHub por última vez con éxito
     * @param message frase para enseñar a Raúl
     */
    record Status(String installedVersion, State state, @Nullable String latestVersion, @Nullable String installerUrl,
                  @Nullable String releaseUrl, @Nullable Instant checkedAt, String message) {}

    /**
     * Estado de las versiones. Mira GitHub solo si hace más de un día de la última comprobación (o más de una hora si salió
     * mal). Nunca lanza: sin red o sin releases, el estado es {@code UNKNOWN}.
     */
    Status status();
}
