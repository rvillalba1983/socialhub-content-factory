package com.socialhub.publishing.application;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Margen mínimo entre ahora y la hora de publicación. Único punto de acceso: quien lo necesite (por ejemplo, «Mover a la
 * siguiente hora libre») lo lee de aquí. Por defecto, 30 minutos; es un ajuste que implementa fase-12-material-faltante: al
 * integrarlo, basta con que {@link #current()} lea ese ajuste.
 */
@Component
class PublishMargin {

    static final Duration DEFAULT = Duration.ofMinutes(30);

    Duration current() { return DEFAULT; }
}
