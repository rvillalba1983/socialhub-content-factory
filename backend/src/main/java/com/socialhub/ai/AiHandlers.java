package com.socialhub.ai;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Manejadores de cada tipo de encargo, aportados por los módulos.
 *
 * <p>Se buscan la primera vez que se usan, no al crear el bean: los manejadores de publicaciones necesitan
 * {@link AiOrders}, que a su vez usa este registro. Así el ciclo se corta aquí, en un solo sitio, y los manejadores no
 * necesitan {@code @Lazy}.
 */
@Component
class AiHandlers {

    private final ObjectProvider<AiTypeHandler> provider;
    private volatile @Nullable Map<AiRequestType, AiTypeHandler> byType;

    AiHandlers(ObjectProvider<AiTypeHandler> provider) {
        this.provider = provider;
    }

    Optional<AiTypeHandler> of(AiRequestType type) { return Optional.ofNullable(handlers().get(type)); }

    private Map<AiRequestType, AiTypeHandler> handlers() {
        Map<AiRequestType, AiTypeHandler> map = byType;
        if (map == null) {
            synchronized (this) {
                map = byType;
                if (map == null) {
                    map = new EnumMap<>(AiRequestType.class);
                    for (AiTypeHandler h : provider.orderedStream().toList()) {
                        if (map.put(h.type(), h) != null) throw new IllegalStateException("Dos manejadores para " + h.type());
                    }
                    byType = map;
                }
            }
        }
        return map;
    }
}
