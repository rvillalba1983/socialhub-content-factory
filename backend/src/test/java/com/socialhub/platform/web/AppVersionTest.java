package com.socialhub.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;

/** La versión que enseña la web sale de la compilación (build-info), y sin ella no falla. */
class AppVersionTest {

    @SuppressWarnings("unchecked")
    private static AppController controller(BuildProperties info) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(info);
        return new AppController(mock(ConfigurableApplicationContext.class), provider);
    }

    @Test
    void devuelveVersionCommitYFechaDeCompilacion() {
        Properties p = new Properties();
        p.setProperty("version", "1.2.7");
        p.setProperty("commit", "a1b2c3d");
        p.setProperty("time", "2026-11-15T10:30:00Z");
        var body = controller(new BuildProperties(p)).getAppVersion().getBody();
        assertThat(body.getVersion()).isEqualTo("1.2.7");
        assertThat(body.getCommit()).isEqualTo("a1b2c3d");
        assertThat(body.getBuildTime().toInstant()).isEqualTo(Instant.parse("2026-11-15T10:30:00Z"));
    }

    @Test
    void sinCommitDiceLocal() {
        Properties p = new Properties();
        p.setProperty("version", "1.2.0");
        p.setProperty("time", "2026-11-15T10:30:00Z");
        assertThat(controller(new BuildProperties(p)).getAppVersion().getBody().getCommit()).isEqualTo("local");
    }

    @Test
    void sinInformacionDeCompilacionNoFalla() {
        var body = controller(null).getAppVersion().getBody();
        assertThat(body.getVersion()).isEqualTo("desconocida");
        assertThat(body.getCommit()).isEqualTo("local");
    }
}
