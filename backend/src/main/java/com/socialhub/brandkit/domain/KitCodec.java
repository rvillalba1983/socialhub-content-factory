package com.socialhub.brandkit.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Convierte el kit de y a JSON ({@code contracts/brand-kit.v1.schema.json}). */
@SecondaryPort
public interface KitCodec {

    /** Lee el JSON del kit. Si la forma no es la del contrato, lanza {@link InvalidKitException} con todos los motivos. */
    KitContent read(String json);

    /** El kit como JSON normalizado, tal como se guarda. */
    String write(KitContent kit);
}
