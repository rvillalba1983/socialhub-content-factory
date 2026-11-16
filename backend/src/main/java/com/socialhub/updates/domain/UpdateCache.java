package com.socialhub.updates.domain;

import java.time.Instant;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** La última comprobación, para no preguntar a GitHub más de una vez al día aunque se reinicie SocialHub. */
@SecondaryPort
public interface UpdateCache {

    /** @param latest la release más nueva que había; null si no había ninguna o la comprobación falló */
    record Checked(Instant at, boolean ok, @Nullable Release latest) {}

    Optional<Checked> load();

    void save(Checked checked);
}
