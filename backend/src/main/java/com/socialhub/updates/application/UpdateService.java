package com.socialhub.updates.application;

import com.socialhub.updates.Updates;
import com.socialhub.updates.domain.Release;
import com.socialhub.updates.domain.ReleaseSource;
import com.socialhub.updates.domain.UpdateCache;
import com.socialhub.updates.domain.Versions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

/** Compara la versión instalada con la última release publicada. Una comprobación al día como mucho. */
@Slf4j
@Service
class UpdateService implements Updates {

    static final Duration EVERY = Duration.ofHours(24);
    /** Si la comprobación salió mal (sin red, por ejemplo), se vuelve a probar al cabo de una hora, no en cada apertura. */
    static final Duration RETRY = Duration.ofHours(1);
    static final String NO_INFO = "No hay información de versiones nuevas";

    private final ReleaseSource source;
    private final UpdateCache cache;
    private final ObjectProvider<BuildProperties> build;
    private final Clock clock;
    private final String os = System.getProperty("os.name", "");

    UpdateService(ReleaseSource source, UpdateCache cache, ObjectProvider<BuildProperties> build, Clock clock) {
        this.source = source;
        this.cache = cache;
        this.build = build;
        this.clock = clock;
    }

    @Override
    public synchronized Status status() {
        BuildProperties info = build.getIfAvailable();
        String installed = info == null ? "desconocida" : info.getVersion();
        Instant now = clock.instant();
        UpdateCache.Checked checked = cache.load().orElse(null);
        if (checked == null || now.isAfter(checked.at().plus(checked.ok() ? EVERY : RETRY))) {
            checked = check(now);
        }
        Release latest = checked.latest();
        if (Versions.parse(installed).isEmpty() || latest == null) {
            return new Status(installed, State.UNKNOWN, latest == null ? null : latest.version(), null, null,
                    checked.ok() ? checked.at() : null, NO_INFO);
        }
        String installer = latest.installerFor(os).orElse(null);
        if (Versions.compare(latest.version(), installed) > 0) {
            return new Status(installed, State.UPDATE_AVAILABLE, latest.version(), installer, latest.pageUrl(), checked.at(),
                    "Hay una versión nueva: " + latest.version() + " (tienes la " + installed + ")");
        }
        return new Status(installed, State.UP_TO_DATE, latest.version(), null, null, checked.at(), "Tienes la última versión");
    }

    private UpdateCache.Checked check(Instant now) {
        UpdateCache.Checked result;
        try {
            @Nullable Release latest = source.releases().stream().filter(r -> Versions.parse(r.version()).isPresent())
                    .max(Comparator.comparing(Release::version, Versions::compare)).orElse(null);
            result = new UpdateCache.Checked(now, true, latest);
        } catch (RuntimeException e) {
            // Sin red o sin acceso a GitHub no es un error: simplemente no hay información
            log.info("No se pudo comprobar si hay una versión nueva: {}", e.getMessage());
            Optional<UpdateCache.Checked> before = cache.load();
            result = new UpdateCache.Checked(now, false, before.map(UpdateCache.Checked::latest).orElse(null));
        }
        try {
            cache.save(result);
        } catch (RuntimeException e) {
            log.info("No se pudo guardar la comprobación de versiones: {}", e.getMessage());
        }
        return result;
    }
}
