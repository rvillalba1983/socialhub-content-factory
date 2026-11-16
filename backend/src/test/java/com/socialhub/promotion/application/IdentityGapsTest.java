package com.socialhub.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandIdentity;
import com.socialhub.promotion.domain.DecisionRepository;
import com.socialhub.promotion.domain.FolderRepository;
import com.socialhub.promotion.domain.IdentityRepository;
import com.socialhub.promotion.domain.PromotionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Huecos de la identidad de la marca: solo cuentan el tono y el público; los temas vetados vacíos son válidos. */
class IdentityGapsTest {

    private final PromotionRepository repository = mock(PromotionRepository.class);
    private final IdentityRepository identities = mock(IdentityRepository.class);
    private final PromotionQueriesService queries =
            new PromotionQueriesService(repository, mock(FolderRepository.class), identities, mock(DecisionRepository.class));

    private void identity(String tone, String audience, List<String> forbiddenTopics) {
        when(repository.brand("b")).thenReturn(Optional.of(new Brand("b", "B", "@b", "#fff", false)));
        when(identities.identity("b")).thenReturn(Optional.of(new BrandIdentity(tone, audience, forbiddenTopics,
                List.of(), List.of(), List.of(), "", "", "", "", "", "")));
    }

    @Test
    void emptyForbiddenTopicsIsNotAGap() {
        identity("Cercano", "Lectores", List.of());
        assertThat(queries.identityGaps("b")).isEmpty();
    }

    @Test
    void onlyToneAndAudienceAreGaps() {
        identity("", "", List.of());
        assertThat(queries.identityGaps("b")).containsExactly("el tono", "el público");
        identity("Cercano", "", List.of("política"));
        assertThat(queries.identityGaps("b")).containsExactly("el público");
    }
}
