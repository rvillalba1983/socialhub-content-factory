package com.socialhub.brandkit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionQueries;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** El manejador del encargo «Kit de marca»: datos de entrada, qué lleva el encargo y qué hace con el resultado. */
class BrandKitHandlerTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final BrandKits kits = mock(BrandKits.class);
    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final BrandKitHandler handler = new BrandKitHandler(kits, promotions, JSON);

    private static AiJob job(String... inputs) {
        return new AiJob("t1", AiRequestType.BRAND_KIT, "poemario", "el-delito", "Kit de marca", "Diseña el kit", List.of(inputs),
                Instant.parse("2026-11-24T20:00:00Z"));
    }

    private static JsonNode outputs(KitContent kit) {
        var templates = JSON.createArrayNode();
        kit.templates().forEach(t -> {
            var node = JSON.createObjectNode().put("id", t.id()).put("piece", t.piece().code()).put("name", t.name()).put("svg", t.svg());
            if (t.image() == null) node.putNull("image");
            else node.set("image", JSON.createObjectNode().put("x", t.image().x()).put("y", t.image().y())
                    .put("width", t.image().width()).put("height", t.image().height()));
            templates.add(node);
        });
        var palette = JSON.createArrayNode();
        kit.palette().forEach(c -> palette.add(JSON.createObjectNode().put("name", c.name()).put("hex", c.hex())));
        var k = JSON.createObjectNode();
        k.set("palette", palette);
        k.set("templates", templates);
        return JSON.createObjectNode().<tools.jackson.databind.node.ObjectNode>set("kit", k).put("summary", "Marco dorado");
    }

    private BrandKit saved(int version) {
        return new BrandKit(7L, "el-delito", version, KitStatus.PENDING, KitFixtures.delito(), "{}", null, "t1", Instant.now(), null);
    }

    private void brand(String promotion, String brand, String... pieces) {
        when(promotions.brandOf(promotion)).thenReturn(brand);
        when(promotions.kitPiecesOf(brand)).thenReturn(List.of(pieces));
    }

    @Test
    void withoutPiecesTheOrderAsksForAllThePiecesTheBrandCarries() {
        brand("poemario", "el-delito", "cita", "cierre", "historia");
        assertThat(handler.completeInputs("poemario", List.of())).containsExactly("pieza:cita", "pieza:cierre", "pieza:historia");
        assertThat(handler.completeInputs("poemario", List.of("pieza:cierre"))).containsExactly("pieza:cierre");
        assertThat(handler.completeInputs("poemario", List.of("comentario:más oscuro")))
                .contains("pieza:cita", "pieza:cierre", "pieza:historia");
        brand("disco", "litoques", "cierre", "historia");
        assertThat(handler.completeInputs("disco", List.of())).containsExactly("pieza:cierre", "pieza:historia");
        // Sin saber las piezas de la marca, lo común a todas
        assertThat(handler.completeInputs(List.of())).containsExactly("pieza:cierre", "pieza:historia");
    }

    @Test
    void aPieceTheBrandDoesNotCarryIsRejected() {
        brand("disco", "litoques", "cierre", "historia");
        assertThatThrownBy(() -> handler.validateOrder("disco", List.of("pieza:cita", "pieza:cierre")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no lleva cita").hasMessageContaining("cierre, historia");
        handler.validateOrder("disco", List.of("pieza:cierre"));
    }

    @Test
    void aSecondKitIsABlockedConflictWhileOneIsPendingOrAnOrderIsOpenInAnyPromotionOfTheBrand() {
        brand("poemario", "el-delito", "cita", "cierre", "historia");
        handler.validateOrder("poemario", List.of("pieza:cierre"));
        when(kits.pending("el-delito")).thenReturn(java.util.Optional.of(saved(1)));
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("pieza:cierre")))
                .isInstanceOf(DomainException.class).hasMessageContaining("kit pendiente");
        when(kits.pending("el-delito")).thenReturn(java.util.Optional.empty());
        // Un encargo abierto en OTRA promoción de la misma marca también cuenta
        when(kits.openOrderPromotion("el-delito")).thenReturn(java.util.Optional.of("segunda"));
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("pieza:cierre")))
                .isInstanceOf(DomainException.class).hasMessageContaining("encargo de kit abierto").hasMessageContaining("segunda");
    }

    @Test
    void orderInputsMustBeKnownPiecesCommentsOrAPreviousKit() {
        brand("poemario", "el-delito", "cita", "cierre", "historia");
        handler.validateOrder("poemario", List.of("pieza:cita", "cierre", "comentario:más claro"));
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("pieza:infografia")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("«pieza:infografia»").hasMessageContaining("cita, cierre, historia");
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("hola")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("«hola» no es una pieza");
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("comentario:solo")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Indica qué piezas");
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("cierre", "kit-anterior:abc")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("número del kit");
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("cierre", "kit-anterior:99")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no existe");
    }

    @Test
    void thePreviousKitMustBeOfTheSameBrand() {
        brand("poemario", "litoques", "cierre", "historia");
        when(kits.find(7L)).thenReturn(java.util.Optional.of(saved(1)));
        assertThatThrownBy(() -> handler.validateOrder("poemario", List.of("cierre", "kit-anterior:7")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es de la marca");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theOrderCarriesTheRulesForEachRequestedPieceAndTheImageRule() {
        when(promotions.brandRefs()).thenReturn(List.of(new PromotionQueries.BrandRef("el-delito", "El DeLito")));
        when(promotions.colorOf("el-delito")).thenReturn("#D23A2E");
        when(promotions.folderMap("poemario")).thenReturn(Map.of("INFO", "G:/info"));
        when(promotions.visualStyleOf("poemario")).thenReturn("");
        when(kits.approved("el-delito")).thenReturn(java.util.Optional.empty());
        Map<String, Object> inputs = handler.inputs(job("pieza:cita", "pieza:historia"));
        assertThat(inputs).containsEntry("brand_name", "El DeLito").containsEntry("brand_color", "#D23A2E")
                .doesNotContainKeys("comment", "previous_kit", "approved_kit", "visual_style");
        List<Map<String, Object>> pieces = (List<Map<String, Object>>) inputs.get("pieces");
        assertThat(pieces).extracting(p -> p.get("piece")).containsExactly("cita", "historia");
        assertThat(pieces.get(0)).containsEntry("required_fields", List.of("campo-cita"))
                .containsEntry("optional_fields", List.of("campo-autor"));
        assertThat(pieces.get(1).get("required_fields")).isEqualTo(List.of("campo-titulo", "campo-texto", "campo-cta"));
        assertThat((List<String>) pieces.get(1).get("ratios")).singleElement().asString().contains("9:16").contains("0 0 1080 1920");
        assertThat(inputs.get("image_rule").toString()).contains("ENCIMA de la ilustración");
        assertThat(inputs.get("fonts").toString()).contains("Crimson Text").contains("Fira Sans").contains("Bebas Neue");
        assertThat(inputs.get("contract").toString()).contains("brand-kit.v1");
        assertThat(inputs.get("template_rules").toString()).contains("campo-NOMBRE").contains("caja-NOMBRE");
    }

    @Test
    void anotherVersionCarriesTheCommentAndThePreviousKit() {
        when(promotions.brandRefs()).thenReturn(List.of());
        when(promotions.colorOf("el-delito")).thenReturn("");
        when(promotions.folderMap("poemario")).thenReturn(Map.of());
        when(promotions.visualStyleOf("poemario")).thenReturn("Navidad");
        when(kits.find(7L)).thenReturn(java.util.Optional.of(new BrandKit(7L, "el-delito", 1, KitStatus.REJECTED, KitFixtures.delito(),
                "{\"palette\":[],\"templates\":[]}", "Más oscuro", "t0", Instant.now(), null)));
        Map<String, Object> inputs = handler.inputs(job("pieza:cierre", "comentario:Más oscuro", "kit-anterior:7"));
        assertThat(inputs).containsEntry("comment", "Más oscuro").containsEntry("visual_style", "Navidad").doesNotContainKey("approved_kit");
        assertThat(inputs.get("previous_kit")).isInstanceOf(JsonNode.class);
        assertThat(inputs.get("expected_outputs").toString()).contains("otra versión");
    }

    @Test
    void aValidKitIsProposedAsPendingAndTheTaskSaysSo() {
        when(kits.check(anyString())).thenReturn(List.of());
        when(kits.propose(eq("el-delito"), anyString(), eq("t1"))).thenReturn(saved(3));
        String text = handler.apply(job("pieza:cita", "pieza:cierre", "pieza:historia"), outputs(KitFixtures.delito()), JSON.createObjectNode());
        assertThat(text).contains("versión 3").contains("pendiente de tu aprobación").contains("5 plantillas").contains("Marco dorado");
        verify(kits).propose(eq("el-delito"), anyString(), eq("t1"));
    }

    @Test
    void anInvalidKitFailsTheOrderWithTheReasonsAndSavesNothing() {
        when(kits.check(anyString())).thenReturn(List.of("La plantilla «cita-4x5» no tiene campo-cita.", "Otro motivo."));
        assertThatThrownBy(() -> handler.apply(job("pieza:cita", "pieza:cierre", "pieza:historia"), outputs(KitFixtures.delito()), JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("El kit no es válido")
                .hasMessageContaining("no tiene campo-cita").hasMessageContaining("Otro motivo");
        verify(kits, never()).propose(anyString(), anyString(), any());
    }

    @Test
    void aKitMissingARequestedPieceOrWithoutKitFails() {
        KitContent onlyCierre = KitFixtures.with(KitFixtures.delitoTemplates().stream().filter(t -> t.piece() == KitPiece.CIERRE).toList());
        assertThatThrownBy(() -> handler.apply(job("pieza:cierre", "pieza:historia"), outputs(onlyCierre), JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Faltan plantillas").hasMessageContaining("historia");
        assertThatThrownBy(() -> handler.apply(job("pieza:cierre"), JSON.createObjectNode().put("summary", "x"), JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outputs.kit");
        verify(kits, never()).propose(anyString(), anyString(), any());
    }

    @Test
    void aKitWithPiecesNobodyAskedForIsRejected() {
        brand("poemario", "el-delito", "cierre", "historia");
        assertThatThrownBy(() -> handler.apply(job("pieza:cierre", "pieza:historia"), outputs(KitFixtures.delito()), JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se pidieron: cita")
                .hasMessageContaining("Piezas pedidas: cierre, historia").hasMessageContaining("la marca solo lleva cierre, historia");
        // Una pieza permitida pero no pedida también sobra
        brand("poemario", "el-delito", "cita", "cierre", "historia");
        assertThatThrownBy(() -> handler.apply(job("pieza:cierre"), outputs(KitFixtures.delito()), JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se pidieron: cita, historia");
        verify(kits, never()).propose(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theReferenceKitCarriesThePaletteAndIdsButTheSvgOnlyOfTheRequestedPieces() {
        when(promotions.brandRefs()).thenReturn(List.of());
        when(promotions.folderMap("poemario")).thenReturn(Map.of());
        when(promotions.colorOf("el-delito")).thenReturn("");
        when(promotions.visualStyleOf("poemario")).thenReturn("");
        when(kits.approved("el-delito")).thenReturn(java.util.Optional.of(new BrandKit(5L, "el-delito", 2, KitStatus.APPROVED,
                KitFixtures.delito(), "{}", null, "t0", Instant.now(), Instant.now())));
        JsonNode reference = (JsonNode) handler.inputs(job("pieza:cierre")).get("approved_kit");
        assertThat(reference.get("version").asInt()).isEqualTo(2);
        assertThat(reference.get("palette")).hasSize(4);
        assertThat(reference.get("templates")).hasSize(5);
        for (JsonNode t : reference.get("templates")) {
            assertThat(t.get("id").asString()).isNotBlank();
            assertThat(t.has("svg")).as(t.get("id").asString()).isEqualTo("cierre".equals(t.get("piece").asString()));
        }
    }

    @Test
    void whenTheOrderWouldBeTooBigTheReferenceKitLosesItsSvgAndSaysSo() {
        when(promotions.brandRefs()).thenReturn(List.of());
        when(promotions.folderMap("poemario")).thenReturn(Map.of());
        when(promotions.colorOf("el-delito")).thenReturn("");
        when(promotions.visualStyleOf("poemario")).thenReturn("");
        List<com.socialhub.brandkit.domain.KitTemplate> big = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            big.add(KitFixtures.template("cierre-" + i, KitPiece.CIERRE, "<svg>" + "x".repeat(60_000) + "</svg>",
                    new com.socialhub.brandkit.domain.ImageArea(0, 0, 1080, 700)));
        }
        when(kits.approved("el-delito")).thenReturn(java.util.Optional.of(new BrandKit(5L, "el-delito", 2, KitStatus.APPROVED,
                KitFixtures.with(big), "{}", null, "t0", Instant.now(), Instant.now())));
        Map<String, Object> inputs = handler.inputs(job("pieza:cierre"));
        JsonNode reference = (JsonNode) inputs.get("approved_kit");
        assertThat(reference.get("templates")).hasSize(11);
        reference.get("templates").forEach(t -> assertThat(t.has("svg")).isFalse());
        assertThat(inputs.get("reference_note").toString()).contains("no lleva los SVG");
        assertThat(JSON.writeValueAsString(inputs).length()).isLessThan(BrandKitHandler.MAX_INPUTS_BYTES);
    }
}
