package com.socialhub.ai;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Comprueba un resultado de Claude contra {@code contracts/task-result.v1.schema.json}. */
@Component
@RequiredArgsConstructor
class ResultValidator {

    private final JsonContracts contracts;

    /** @return los errores, vacía si cumple el contrato */
    List<String> validate(JsonNode result) { return contracts.validate(JsonContracts.TASK_RESULT, result); }
}
