package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Qué encargos son urgentes: lo pedido a mano, lo que vence antes de la noche y el contenido de publicaciones cercanas. */
class AiUrgentRuleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(MADRID).toInstant();
    }

    private final AiUrgentRule rule = new AiUrgentRule(48);

    @Test
    void anOrderAskedByHandIsAlwaysUrgentEvenWithoutDeadlineOrNearPublications() {
        Instant now = madrid(9, 11, 0);
        assertThat(rule.urgent(false, true, null, null, now)).isTrue();
        assertThat(rule.urgent(false, false, null, null, now)).isFalse();
    }

    @Test
    void contentIsUrgentWhenAPublicationLeavesInLessThan48HoursFromWhenClaudeCanSeeIt() {
        Instant now = madrid(9, 11, 0);
        // Visible a las 11:05:30: el límite son las 11:05:30 del día 11
        assertThat(rule.urgent(false, false, null, madrid(11, 11, 5), now)).isTrue();
        assertThat(rule.urgent(false, false, null, madrid(11, 11, 6), now)).isFalse();
        assertThat(rule.urgent(false, false, null, madrid(14, 12, 0), now)).isFalse();
        // Una publicación que ya pasó o está al caer también cuenta
        assertThat(rule.urgent(false, false, null, madrid(9, 12, 0), now)).isTrue();
    }

    @Test
    void theMarginIsConfigurable() {
        Instant now = madrid(9, 11, 0);
        assertThat(new AiUrgentRule(24).urgent(false, false, null, madrid(11, 0, 0), now)).isFalse();
        assertThat(new AiUrgentRule(72).urgent(false, false, null, madrid(11, 12, 0), now)).isTrue();
    }
}
