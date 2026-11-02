package com.socialhub.ai;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Comprueba un resultado de Claude contra {@code contracts/task-result.v1.schema.json} (copiado al classpath). */
@Component
class ResultValidator {

    private final Schema schema;

    ResultValidator() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contracts/task-result.v1.schema.json")) {
            if (in == null) throw new IllegalStateException("Falta contracts/task-result.v1.schema.json en el classpath");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(text);
        }
    }

    /** @return los errores, vacía si cumple el contrato */
    List<String> validate(JsonNode result) {
        List<Error> errors = schema.validate(result);
        return errors.stream().map(Error::toString).toList();
    }
}
