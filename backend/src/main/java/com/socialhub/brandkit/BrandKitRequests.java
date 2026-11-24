package com.socialhub.brandkit;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Pedir a Claude el kit de una marca (encargo {@code socialhub.brand-kit}) o una versión nueva del que no convenció. */
@PrimaryPort
public interface BrandKitRequests {

    /**
     * Pide el kit de la marca. El encargo cuelga de una promoción suya: si la marca tiene varias hay que elegirla; una marca
     * sin promociones no puede pedirlo.
     *
     * @param promotionId promoción de la marca; puede faltar si la marca solo tiene una
     * @param pieces códigos de pieza ({@code cita}, {@code cierre}, {@code historia}) de las que lleva la marca; vacío: todas
     *               las que lleva la marca
     * @throws com.socialhub.platform.DomainException (409) si ya hay un kit pendiente o un encargo de kit abierto
     * @return identificador de la tarea
     */
    String request(String brandId, @Nullable String promotionId, List<String> pieces);

    /**
     * Rechaza el kit pendiente con el comentario de Raúl y, si {@code another}, pide ya otra versión con ese comentario
     * (las mismas piezas que el kit rechazado y, por defecto, su misma promoción).
     *
     * @param kitId el kit que Raúl está viendo: si ya no es el pendiente de la marca, lanza {@code DomainException} (409)
     * @return identificador de la tarea nueva, si se pidió
     */
    Optional<String> reject(String brandId, long kitId, String comment, @Nullable String promotionId, boolean another);
}
