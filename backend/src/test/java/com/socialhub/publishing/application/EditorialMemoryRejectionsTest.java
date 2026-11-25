package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.EditorialMemoryRepository;
import com.socialhub.publishing.domain.EditorialMemoryRepository.Kind;
import com.socialhub.publishing.domain.EditorialMemoryRepository.Rejection;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.RejectionReason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Arreglo I: las cancelaciones de una limpieza masiva no viajan a Claude como «rechazos»; las que tienen motivo propio, sí. */
class EditorialMemoryRejectionsTest {

    private static Rejection rejection(String id, String note) {
        return new Rejection("el-delito", "primera", id, Kind.PUBLICATION, RejectionReason.OTHER, note, "Título " + id, "Texto", Instant.now());
    }

    @Test
    void massCleanupIsRecognisedByItsNoteOnly() {
        assertThat(EditorialMemoryService.massCleanup(rejection("a", "Limpieza antes de la prueba de cero"))).isTrue();
        assertThat(EditorialMemoryService.massCleanup(rejection("b", "  limpieza antes de la PRUEBA de cero"))).isTrue();
        assertThat(EditorialMemoryService.massCleanup(rejection("c", "Demasiado solemne para una historia"))).isFalse();
        assertThat(EditorialMemoryService.massCleanup(rejection("d", null))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theContextOfAnOrderLeavesOutTheCleanupAndKeepsTheRealRejectionsEvenWhenTheCleanupIsTheMostRecent() {
        var promotions = mock(PromotionQueries.class);
        var publications = mock(PublicationRepository.class);
        var memory = mock(EditorialMemoryRepository.class);
        when(promotions.promotionIdsOf(anyString())).thenReturn(List.of("primera"));
        when(promotions.decisionRefs(anyString())).thenReturn(List.of());
        when(publications.findAll()).thenReturn(List.of());
        List<Rejection> all = new ArrayList<>();
        for (int i = 0; i < 11; i++) all.add(rejection("limpia-" + i, "Limpieza antes de la prueba de cero"));
        all.add(rejection("real", "Demasiado solemne para una historia"));
        when(memory.rejections(anyString(), anyInt())).thenAnswer(i -> all.stream().limit((int) i.getArgument(1)).toList());
        when(memory.corrections(anyString(), anyInt())).thenReturn(List.of());
        var service = new EditorialMemoryService(promotions, publications, memory, Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("UTC")));

        var memoryForClaude = (Map<String, Object>) service.context("primera", "el-delito").get("editorial_memory");
        var sent = (List<Map<String, Object>>) memoryForClaude.get("rejections");

        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().get("note")).isEqualTo("Demasiado solemne para una historia");
    }
}
