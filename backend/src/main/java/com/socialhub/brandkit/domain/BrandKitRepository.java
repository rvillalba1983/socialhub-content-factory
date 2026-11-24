package com.socialhub.brandkit.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Las versiones guardadas del kit de cada marca. */
@SecondaryPort
public interface BrandKitRepository {

    /** Guarda (si no tiene id) o actualiza el estado de una versión, y la devuelve con su id. */
    BrandKit save(BrandKit kit);

    Optional<BrandKit> find(long id);

    Optional<BrandKit> findByStatus(String brandId, KitStatus status);

    boolean brandExists(String brandId);

    /** El número de la próxima versión de la marca (1 si no hay ninguna). */
    int nextVersion(String brandId);
}
