package com.socialhub.publishing.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Claves de servicios externos (por ahora, la de Publora). Viven en un archivo de la carpeta de datos de SocialHub,
 * legible solo por el usuario: nunca en la base de datos, en las copias de Drive ni en el repositorio.
 */
@SecondaryPort
public interface SecretStore {

    Optional<String> get(String name);

    void put(String name, String value);

    void delete(String name);
}
