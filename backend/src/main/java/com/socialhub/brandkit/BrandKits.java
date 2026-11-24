package com.socialhub.brandkit;

import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.TemplatePreview;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Kits de marca: guardar el que devuelve Claude, aprobarlo o rechazarlo y consultar el de cada marca. */
@PrimaryPort
public interface BrandKits {

    /**
     * Comprueba el kit (objeto {@code outputs.kit}, JSON) sin guardar nada ni lanzar excepciones: los motivos, en español;
     * vacía si vale. Para validar antes de {@link #propose} dentro de una transacción ajena.
     */
    List<String> check(String kitJson);

    /**
     * Valida el kit (objeto {@code outputs.kit}, JSON) y lo guarda como pendiente de aprobación. Si ya había otro pendiente
     * de esa marca, pasa a sustituido. Si el kit no vale, lanza {@code InvalidKitException} con todos los motivos.
     */
    BrandKit propose(String brandId, String kitJson, @Nullable String sourceTaskId);

    /** Aprueba un kit pendiente; el aprobado anterior de la marca pasa a sustituido. */
    BrandKit approve(long id);

    /** Rechaza un kit pendiente con el comentario de «Pedir otra versión». */
    BrandKit reject(long id, String comment);

    /** Una versión del kit por su identificador. */
    Optional<BrandKit> find(long id);

    /** Vista previa de cada plantilla del kit, con textos de ejemplo, en el mismo orden que sus plantillas. */
    List<TemplatePreview> previews(BrandKit kit);

    /** Kit en vigor de la marca (el aprobado). */
    Optional<BrandKit> approved(String brandId);

    /** Kit que espera la decisión de Raúl. */
    Optional<BrandKit> pending(String brandId);
}
