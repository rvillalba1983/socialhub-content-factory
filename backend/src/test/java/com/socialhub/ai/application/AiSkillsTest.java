package com.socialhub.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.ai.domain.AiRequestType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Qué skills viajan en cada encargo y qué reglas fijas, aunque la skill no exista (fase 13, bloque 4). */
class AiSkillsTest {

    private static Map<String, Object> publication(String platform, String type) {
        return Map.of("platform", platform, "type", type);
    }

    private static Map<String, Object> lot(Map<String, Object>... publications) {
        return Map.of("publications", List.of(publications));
    }

    @SafeVarargs
    private static List<String> spec(Map<String, Object>... publications) {
        return AiSkills.forOrder(AiRequestType.WEEKLY_SPEC, lot(publications));
    }

    @Test
    void aSpecOnlyLoadsTheSkillsOfThePieceTypesItHas() {
        assertThat(spec(publication("INSTAGRAM", "Carrusel"))).containsExactly("brand-profile", "carousel-writer", "hook-writer",
                "platform-specs-and-validation");
        assertThat(spec(publication("INSTAGRAM", "Historia"))).containsExactly("brand-profile", "story-writer", "hook-writer",
                "platform-specs-and-validation");
        assertThat(spec(publication("TIKTOK", "Reel"))).containsExactly("brand-profile", "hook-writer", "platform-specs-and-validation");
    }

    @Test
    void tiktokCarouselsUsePhotoModeAndInstagramOnesTheCarouselWriter() {
        assertThat(spec(publication("TIKTOK", "Carrusel: un poema corto"))).contains("tiktok-photo-mode").doesNotContain("carousel-writer");
        assertThat(spec(publication("INSTAGRAM", "Carrusel"), publication("TIKTOK", "Carrusel"), publication("INSTAGRAM", "Historia")))
                .containsExactly("brand-profile", "carousel-writer", "tiktok-photo-mode", "story-writer", "hook-writer",
                        "platform-specs-and-validation");
    }

    @Test
    void quoteCardsAddTheQuoteSkillAndAccentsAndCaseDoNotMatter() {
        assertThat(spec(publication("INSTAGRAM", "CITA"))).contains("quote-cards-and-text-graphics");
        assertThat(spec(publication("instagram", "Carrusel"))).contains("carousel-writer");
    }

    @Test
    void theSkillsNotActivatedForElDelitoNeverTravel() {
        List<String> everything = new java.util.ArrayList<>();
        for (AiRequestType type : AiRequestType.values()) {
            everything.addAll(AiSkills.forOrder(type, lot(publication("INSTAGRAM", "Carrusel"), publication("TIKTOK", "Historia"),
                    publication("INSTAGRAM", "Cita"), publication("TIKTOK", "Infografía"), publication("INSTAGRAM", "Meme"))));
        }
        assertThat(everything).doesNotContain("infographic-and-data-viz", "meme-and-culture", "thumbnail-design", "pinterest-pin-design");
    }

    @Test
    void textsReviewAndKitHaveTheirOwnAndTheRestOfTheOrdersHaveNone() {
        assertThat(AiSkills.forOrder(AiRequestType.CAPTIONS, Map.of())).containsExactly("brand-profile", "caption-writer", "hook-writer",
                "platform-specs-and-validation");
        assertThat(AiSkills.forOrder(AiRequestType.QUALITY_REVIEW, Map.of("publication", publication("TIKTOK", "Reel"))))
                .containsExactly("platform-specs-and-validation");
        assertThat(AiSkills.forOrder(AiRequestType.BRAND_KIT, Map.of())).containsExactly("brand-profile", "design-and-templates", "dataviz",
                "frontend-design");
        for (AiRequestType type : List.of(AiRequestType.WEEKLY_BALANCE, AiRequestType.RESEARCH, AiRequestType.WEEKLY_PROPOSALS,
                AiRequestType.PUBLER_SCHEDULE, AiRequestType.PUBLISH_CHECK, AiRequestType.PUBLIC_LINK, AiRequestType.CALENDAR_READ,
                AiRequestType.METRICS_READ)) {
            assertThat(AiSkills.forOrder(type, lot(publication("INSTAGRAM", "Carrusel")))).as(type.name()).isEmpty();
        }
    }

    @Test
    void theNoteSaysWhatToDoIfASkillIsMissing() {
        assertThat(AiSkills.NOTE).contains("Skill").contains("warnings").contains("sin ella");
    }

    @Test
    void theFixedRulesTravelWithEveryOrderThatUsesSkillsAndTheCitationsAskForTheFullPath() {
        for (AiRequestType type : List.of(AiRequestType.WEEKLY_SPEC, AiRequestType.CAPTIONS, AiRequestType.BRAND_KIT, AiRequestType.QUALITY_REVIEW)) {
            assertThat(AiStandingRules.forType(type)).as(type.name()).contains(AiStandingRules.NO_AI_IMAGES, AiStandingRules.LITERAL_VERSES,
                    AiStandingRules.BRAND_COLOR, AiStandingRules.NEVER_SILENT);
        }
        assertThat(AiStandingRules.forType(AiRequestType.RESEARCH)).doesNotContain(AiStandingRules.NO_AI_IMAGES).contains(AiStandingRules.NEVER_SILENT);
        assertThat(AiStandingRules.NO_AI_IMAGES).contains("Pinterest").contains("YouTube");
        assertThat(AiStandingRules.CITATIONS).contains("RUTA COMPLETA").contains("sin paréntesis");
    }

    @Test
    void theCallToActionTravelsWithAllItsFormulationsAndTheRuleToVaryThem() {
        var extra = AiWritingRules.forOrder(AiRequestType.CAPTIONS, Map.of("call_to_action", "Busca a Lito\n\nComenta DELITO\nBusca a Lito"),
                Map.of("publications", List.of(publication("INSTAGRAM", "Post"))));
        assertThat(extra.get("call_to_action_options")).isEqualTo(List.of("Busca a Lito", "Comenta DELITO"));
        assertThat((String) extra.get("call_to_action_rule")).contains("distinta").contains("la de la marca");
        // Sin CTA en la marca no hay regla; en otros encargos tampoco
        assertThat(AiWritingRules.forOrder(AiRequestType.WEEKLY_SPEC, Map.of(), Map.of())).isEmpty();
        assertThat(AiWritingRules.forOrder(AiRequestType.WEEKLY_BALANCE, Map.of("call_to_action", "x"), Map.of())).isEmpty();
        assertThat(AiWritingRules.forOrder(AiRequestType.WEEKLY_SPEC, Map.of("call_to_action", "x"), Map.of())).containsKey("call_to_action_rule")
                .doesNotContainKey("hashtags_by_network");
    }

    @Test
    void hashtagsAreAskedPerNetworkOnlyForTheNetworksInTheLot() {
        var extra = AiWritingRules.forOrder(AiRequestType.CAPTIONS, Map.of(),
                Map.of("publications", List.of(publication("TIKTOK", "Reel"), publication("TIKTOK", "Carrusel"))));
        @SuppressWarnings("unchecked") var byNetwork = (Map<String, String>) extra.get("hashtags_by_network");
        assertThat(byNetwork).containsOnlyKeys("TIKTOK");
        assertThat(byNetwork.get("TIKTOK")).contains("Sin etiquetas propias de Instagram").contains("#bookstagram");
        assertThat((String) extra.get("hashtags_rule")).contains("nunca las de otra");
    }
}
