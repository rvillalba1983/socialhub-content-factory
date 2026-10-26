package com.socialhub;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Cada dominio solo usa la parte pública de los demás (su paquete raíz). */
class ModularityTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(SocialHubApplication.class).verify();
    }
}
