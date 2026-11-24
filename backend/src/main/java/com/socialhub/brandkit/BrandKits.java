package com.socialhub.brandkit;

import com.socialhub.brandkit.domain.BrandKit;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Kits de marca: guardar el que devuelve Claude, aprobarlo o rechazarlo y consultar el de cada marca. */
@PrimaryPort
public interface BrandKits {

    /**
     * Valida el kit (objeto {@code outputs.kit}, JSON) y lo guarda como pendiente de aprobación. Si ya había otro pendiente
     * de esa marca, pasa a sustituido. Si el kit no vale, lanza {@code InvalidKitException} con todos los motivos.
     */
    BrandKit propose(String brandId, String kitJson, @Nullable String sourceTaskId);

    /** Aprueba un kit pendiente; el aprobado anterior de la marca pasa a sustituido. */
    BrandKit approve(long id);

    /** Rechaza un kit pendiente con el comentario de «Pedir otra versión». */
    BrandKit reject(long id, String comment);

    /** Kit en vigor de la marca (el aprobado). */
    Optional<BrandKit> approved(String brandId);

    /** Kit que espera la decisión de Raúl. */
    Optional<BrandKit> pending(String brandId);
}
