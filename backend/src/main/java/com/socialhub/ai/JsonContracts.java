package com.socialhub.ai;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Valida JSON contra los contratos de {@code contracts/} (copiados al classpath al compilar). */
@Component
public class JsonContracts {

    public static final String TASK_RESULT = "task-result.v1";
    public static final String PROMO_SPEC = "promo-spec.v1";

    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    /** @return los errores (como mucho 5), vacía si cumple el contrato */
    public List<String> validate(String contract, JsonNode node) {
        List<Error> errors = schemas.computeIfAbsent(contract, JsonContracts::load).validate(node);
        return errors.stream().limit(5).map(Error::toString).toList();
    }

    private static Schema load(String contract) {
        try (InputStream in = JsonContracts.class.getResourceAsStream("/contracts/" + contract + ".schema.json")) {
            if (in == null) throw new IllegalStateException("Falta contracts/" + contract + ".schema.json en el classpath");
            // El «$id» de los contratos es un nombre, no una URI: la librería no lo admite y se quita al cargarlo
            ObjectNode node = (ObjectNode) JsonMapper.builder().build()
                    .readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            node.remove("$id");
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(node.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
