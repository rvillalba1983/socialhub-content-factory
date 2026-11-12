package com.socialhub.ai;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Qué encargos son urgentes, es decir, los que también hacen las pasadas de Claude de las 8:00 a las 18:00. Lo es el
 * encargo que:
 * <ul>
 *   <li>pidió Raúl a mano (o reintentó él): se hace en la siguiente pasada, aunque sea de día;</li>
 *   <li>vence antes de que le llegue la pasada de la noche ({@link ClaudePasses#urgent});</li>
 *   <li>es de contenido (spec, textos, revisión de calidad) y alguna de sus publicaciones sale en menos de
 *   {@code socialhub.ai.near-content-hours} horas (por defecto 48), contadas desde que Claude puede verlo.</li>
 * </ul>
 */
@Component
class AiUrgentRule {

    static final long DEFAULT_NEAR_HOURS = 48;

    private final Duration near;

    AiUrgentRule(@Value("${socialhub.ai.near-content-hours:" + DEFAULT_NEAR_HOURS + "}") long nearHours) {
        this.near = Duration.ofHours(nearHours);
    }

    boolean urgent(boolean onPc, boolean manual, @Nullable Instant deadline, @Nullable Instant contentDue, Instant now) {
        Instant visible = ClaudePickups.visibleAt(now);
        if (manual || ClaudePasses.urgent(onPc, deadline, visible)) return true;
        return contentDue != null && contentDue.isBefore(visible.plus(near));
    }

    /** Solo por contenido cercano (sin «deadline» ni a mano): lo que {@link AiUrgency} comprueba con el tiempo. */
    boolean nearContent(@Nullable Instant contentDue, Instant now) {
        return contentDue != null && contentDue.isBefore(ClaudePickups.visibleAt(now).plus(near));
    }
}
