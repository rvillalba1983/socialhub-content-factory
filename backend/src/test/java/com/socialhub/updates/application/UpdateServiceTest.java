package com.socialhub.updates.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialhub.updates.Updates;
import com.socialhub.updates.domain.Release;
import com.socialhub.updates.domain.ReleaseSource;
import com.socialhub.updates.domain.UpdateCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

/** Aviso de versión nueva: una comprobación al día, sin romper sin red ni sin releases. */
class UpdateServiceTest {

    static final Instant T0 = Instant.parse("2026-11-16T10:00:00Z");

    final AtomicInteger calls = new AtomicInteger();
    List<Release> releases = List.of();
    boolean offline = false;
    Instant now = T0;
    UpdateCache.Checked stored;

    final ReleaseSource source = () -> {
        calls.incrementAndGet();
        if (offline) throw new ReleaseSource.Unavailable("sin red");
        return releases;
    };
    final UpdateCache cache = new UpdateCache() {
        @Override public Optional<Checked> load() { return Optional.ofNullable(stored); }
        @Override public void save(Checked checked) { stored = checked; }
    };

    @SuppressWarnings("unchecked")
    private Updates service(String installed) {
        ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
        Properties p = new Properties();
        p.setProperty("version", installed);
        when(build.getIfAvailable()).thenReturn(new BuildProperties(p));
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        return new UpdateService(source, cache, build, clock);
    }

    private static Release release(String version) {
        return new Release(version, "https://github.com/x/y/releases/tag/v" + version, List.of(
                new Release.Asset("SocialHub-" + version + ".msi", "https://github.com/x/y/releases/download/v" + version + "/SocialHub-" + version + ".msi"),
                new Release.Asset("SocialHub-" + version + ".dmg", "https://github.com/x/y/releases/download/v" + version + "/SocialHub-" + version + ".dmg")));
    }

    @Test
    void aNewerReleaseIsAnnouncedWithItsInstallerAndTheOldestIsIgnored() {
        releases = List.of(release("1.2.0"), release("1.10.1"), release("1.9.0"));
        Updates.Status s = service("1.2.7").status();
        assertThat(s.state()).isEqualTo(Updates.State.UPDATE_AVAILABLE);
        assertThat(s.latestVersion()).isEqualTo("1.10.1");
        assertThat(s.installedVersion()).isEqualTo("1.2.7");
        assertThat(s.releaseUrl()).endsWith("v1.10.1");
        assertThat(s.message()).contains("1.10.1").contains("1.2.7");
        // El instalador es el de este sistema: .msi en Windows, .dmg en macOS y nada en otros
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) assertThat(s.installerUrl()).endsWith("SocialHub-1.10.1.msi");
        else if (os.contains("mac")) assertThat(s.installerUrl()).endsWith("SocialHub-1.10.1.dmg");
        else assertThat(s.installerUrl()).isNull();
    }

    @Test
    void theSameOrOlderReleaseIsUpToDate() {
        releases = List.of(release("1.2.7"));
        assertThat(service("1.2.7").status().state()).isEqualTo(Updates.State.UP_TO_DATE);
        assertThat(service("1.3.0").status().state()).isEqualTo(Updates.State.UP_TO_DATE);
    }

    @Test
    void githubIsAskedAtMostOncePerDayAndTheResultSurvivesARestart() {
        releases = List.of(release("1.3.0"));
        service("1.2.7").status();
        now = T0.plus(Duration.ofHours(23));
        service("1.2.7").status();
        assertThat(calls).hasValue(1);
        now = T0.plus(Duration.ofHours(25));
        service("1.2.7").status();
        assertThat(calls).hasValue(2);
    }

    @Test
    void withoutNetworkItDoesNotFailAndRetriesAfterAnHourKeepingWhatWasKnown() {
        releases = List.of(release("1.3.0"));
        service("1.2.7").status();
        offline = true;
        now = T0.plus(Duration.ofHours(25));
        Updates.Status s = service("1.2.7").status();
        assertThat(s.state()).as("sigue sabiendo la última que vio").isEqualTo(Updates.State.UPDATE_AVAILABLE);
        assertThat(calls).hasValue(2);
        now = now.plus(Duration.ofMinutes(30));
        service("1.2.7").status();
        assertThat(calls).as("no insiste en cada apertura").hasValue(2);
        now = now.plus(Duration.ofMinutes(40));
        service("1.2.7").status();
        assertThat(calls).hasValue(3);
    }

    @Test
    void noReleasesOrNoNetworkAtAllIsUnknownWithoutError() {
        Updates.Status none = service("1.2.7").status();
        assertThat(none.state()).isEqualTo(Updates.State.UNKNOWN);
        assertThat(none.message()).isEqualTo("No hay información de versiones nuevas");
        stored = null;
        offline = true;
        Updates.Status down = service("1.2.7").status();
        assertThat(down.state()).isEqualTo(Updates.State.UNKNOWN);
        assertThat(down.message()).isEqualTo("No hay información de versiones nuevas");
        assertThat(down.checkedAt()).isNull();
    }

    @Test
    void aBuildWithoutVersionNumberIsUnknown() {
        releases = List.of(release("1.3.0"));
        assertThat(service("desconocida").status().state()).isEqualTo(Updates.State.UNKNOWN);
    }
}
