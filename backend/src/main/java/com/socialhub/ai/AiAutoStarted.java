package com.socialhub.ai;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Promociones que SocialHub ha pasado solo de «Planificada» a «Activa». Se anota en {@code ai_auto_order} (sin migración)
 * con el tipo {@link #TYPE} y el día de la activación en {@code week_start}. Si Raúl la activa a mano, no hay anotación.
 */
@Component
@RequiredArgsConstructor
public class AiAutoStarted {

    static final String TYPE = "PROMOTION_AUTO_STARTED";

    private final AiAutoOrderRepository repository;

    /** Las promociones que se activaron solas ese día (hora de España). */
    public Set<String> on(LocalDate day) {
        return repository.findByTypeAndWeekStart(TYPE, day).stream()
                .map(AiAutoOrderEntity::getPromotionId).collect(Collectors.toSet());
    }
}
