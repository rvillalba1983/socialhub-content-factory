package com.socialhub.ai.application;

import com.socialhub.ai.AiAutoStarted;
import com.socialhub.ai.domain.AiAutoOrder;
import com.socialhub.ai.domain.AiAutoOrderRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Se anota en {@code ai_auto_order} (sin migración) con el tipo {@link #TYPE} y el día de la activación en
 * {@code week_start}.
 */
@Service
@RequiredArgsConstructor
class AiAutoStartedService implements AiAutoStarted {

    static final String TYPE = "PROMOTION_AUTO_STARTED";

    private final AiAutoOrderRepository repository;

    @Override
    public Set<String> on(LocalDate day) {
        return repository.ofTypeAndWeek(TYPE, day).stream().map(AiAutoOrder::promotionId).collect(Collectors.toSet());
    }
}
