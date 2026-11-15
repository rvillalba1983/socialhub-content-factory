package com.socialhub.ai.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Dónde se guardan los ajustes del orquestador (límite diario y modelo de cada tipo). */
@SecondaryPort
public interface AiSettingStore {

    Optional<String> get(String key);

    void put(String key, String value);
}
