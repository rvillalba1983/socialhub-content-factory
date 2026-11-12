package com.socialhub.promotion.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Qué carpeta de Encargos tenía la marca cuando Raúl marcó que su carpeta «publico» ya estaba compartida.
 * SocialHub no puede comprobar el permiso de Drive: solo recuerda lo que Raúl marca.
 */
@SecondaryPort
public interface PublicShareMemo {

    Optional<String> markedFor(String brandId);

    void mark(String brandId, String ordersFolder);

    void clear(String brandId);
}
