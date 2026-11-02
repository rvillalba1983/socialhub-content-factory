package com.socialhub.ai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Manejadores de cada tipo de encargo, aportados por los módulos. */
@Component
class AiHandlers {

    private final Map<AiRequestType, AiTypeHandler> byType = new EnumMap<>(AiRequestType.class);

    AiHandlers(List<AiTypeHandler> handlers) {
        for (AiTypeHandler h : handlers) {
            if (byType.put(h.type(), h) != null) throw new IllegalStateException("Dos manejadores para " + h.type());
        }
    }

    Optional<AiTypeHandler> of(AiRequestType type) { return Optional.ofNullable(byType.get(type)); }
}
