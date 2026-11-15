package com.socialhub.ai;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import tools.jackson.databind.JsonNode;

/** Valida JSON contra los contratos de {@code contracts/} (copiados al classpath al compilar). */
@PrimaryPort
public interface JsonContracts {

    String TASK_RESULT = "task-result.v1";
    String PROMO_SPEC = "promo-spec.v1";

    /** @return los errores (como mucho 5), vacía si cumple el contrato */
    List<String> validate(String contract, JsonNode node);
}
