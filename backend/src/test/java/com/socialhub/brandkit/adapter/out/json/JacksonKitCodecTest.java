package com.socialhub.brandkit.adapter.out.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class JacksonKitCodecTest {

    private final JacksonKitCodec codec = new JacksonKitCodec();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Schema schema() throws IOException {
        // El contrato vive en contracts/ (en la raíz del repositorio); el «$id» no es una URI y se quita, como hace el backend
        Path file = Path.of("..", "contracts", "brand-kit.v1.schema.json");
        ObjectNode node = (ObjectNode) JsonMapper.builder().build().readTree(Files.readString(file, StandardCharsets.UTF_8));
        node.remove("$id");
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(node.toString());
    }

    @Test
    void writeThenReadGivesTheSameKit() {
        KitContent kit = KitFixtures.delito();
        assertThat(codec.read(codec.write(kit))).isEqualTo(kit);
    }

    @Test
    void theWrittenKitFollowsTheContractSchema() throws IOException {
        JsonNode json = mapper.readTree(codec.write(KitFixtures.delito()));
        assertThat(schema().validate(json)).isEmpty();
        // y el esquema rechaza lo que el lector también rechaza
        ((ObjectNode) json).putArray("palette").addObject().put("name", "x").put("hex", "rojo");
        assertThat(schema().validate(json)).isNotEmpty();
    }

    @Test
    void nullNotesAndImageAreAccepted() {
        String json = codec.write(KitFixtures.with(KitFixtures.delitoTemplates().subList(0, 1)));
        assertThat(json).contains("\"notes\":null").contains("\"image\":null");
        assertThat(codec.read(json).notes()).isNull();
        assertThat(codec.read(json.replace("\"notes\":null,", "")).notes()).isNull();
    }

    @Test
    void invalidJsonAndWrongShapesGiveSpanishErrors() {
        assertThatThrownBy(() -> codec.read("{no")).isInstanceOf(InvalidKitException.class).hasMessageContaining("no es un JSON válido");
        assertThatThrownBy(() -> codec.read("[]")).isInstanceOf(InvalidKitException.class).hasMessageContaining("debe ser un objeto JSON");
        assertThatThrownBy(() -> codec.read("{}")).isInstanceOf(InvalidKitException.class)
                .satisfies(e -> assertThat(((InvalidKitException) e).errors())
                        .containsExactly("Falta «palette» (una lista).", "Falta «templates» (una lista)."));
    }

    @Test
    void unknownPropertiesPiecesAndBadTypesAreReported() throws IOException {
        ObjectNode kit = (ObjectNode) mapper.readTree(codec.write(KitFixtures.delito()));
        kit.put("extra", 1);
        ObjectNode first = (ObjectNode) kit.get("templates").get(0);
        first.put("piece", "poster");
        ((ObjectNode) kit.get("templates").get(1)).put("svg", 5);
        ((ObjectNode) kit.get("templates").get(2)).put("image", "arriba");
        ((ObjectNode) kit.get("templates").get(3)).putObject("image").put("x", 0).put("y", 0).put("width", "ancho").put("height", 5);
        kit.put("notes", 7);
        assertThatThrownBy(() -> codec.read(kit.toString())).isInstanceOf(InvalidKitException.class)
                .satisfies(e -> assertThat(((InvalidKitException) e).errors()).contains(
                        "Propiedad desconocida «extra» en el kit.",
                        "La plantilla «cita-4x5» tiene una pieza desconocida («poster»): usa cita, cierre, historia (infografia y meme aún no se activan).",
                        "Falta «svg» (un texto) en La plantilla «cita-9x16».",
                        "La plantilla «cierre-4x5»: «image» debe ser null o un objeto {x, y, width, height}.",
                        "La plantilla «cierre-9x16»: en «image», «width» debe ser un número.",
                        "«notes» debe ser un texto."));
    }

    @Test
    void infographyAndMemeAreReadSoTheValidatorCanExplainTheyAreNotActive() throws IOException {
        ObjectNode kit = (ObjectNode) mapper.readTree(codec.write(KitFixtures.delito()));
        ((ObjectNode) kit.get("templates").get(0)).put("piece", "infografia");
        assertThat(codec.read(kit.toString()).templates().getFirst().piece().code()).isEqualTo("infografia");
    }

    @Test
    void aTemplateWithoutIdIsIdentifiedByItsNameInTheErrors() throws IOException {
        ObjectNode kit = (ObjectNode) mapper.readTree(codec.write(KitFixtures.delito()));
        ((ObjectNode) kit.get("templates").get(0)).remove("id");
        ObjectNode second = (ObjectNode) kit.get("templates").get(1);
        second.remove("id");
        second.remove("name");
        second.put("extra", 1);
        assertThatThrownBy(() -> codec.read(kit.toString())).isInstanceOf(InvalidKitException.class)
                .satisfies(e -> assertThat(((InvalidKitException) e).errors()).contains(
                        "Falta «id» (un texto, por ejemplo «cierre-4x5») en la plantilla sin «id» (llamada «Plantilla cita-4x5»).",
                        "Falta «id» (un texto, por ejemplo «cierre-4x5») en la plantilla sin «id».",
                        "Propiedad desconocida «extra» en la plantilla sin «id»."));
    }
}
