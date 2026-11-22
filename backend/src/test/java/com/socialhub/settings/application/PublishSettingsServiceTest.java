package com.socialhub.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.socialhub.settings.PublishSettings;
import com.socialhub.settings.domain.SettingStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Los dos márgenes mínimos de publicación: valores por defecto, límites y cuál se aplica a cada red. */
class PublishSettingsServiceTest {

    private final Map<String, String> data = new HashMap<>();
    private final SettingStore store = new SettingStore() {
        @Override public Optional<String> get(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public void put(String key, String value) { data.put(key, value); }
    };
    private final PublishSettingsService service = new PublishSettingsService(store, mock(ApplicationEventPublisher.class));

    @Test
    void withNothingSavedTheGeneralMarginIsThirtyMinutesAndTikTokEightHours() {
        assertThat(service.minMarginFor("INSTAGRAM")).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.minMarginFor(PublishSettings.TIKTOK)).isEqualTo(Duration.ofHours(8));
        assertThat(service.minMargin()).isEqualTo(Duration.ofMinutes(30));
        var view = service.view();
        assertThat(view.minMarginMinutes()).isEqualTo(30);
        assertThat(view.tiktokMinMarginMinutes()).isEqualTo(480);
        assertThat(view.tiktokDefaultMinutes()).isEqualTo(480);
    }

    @Test
    void eachNetworkUsesItsOwnMargin() {
        service.update(45, 600);
        assertThat(service.minMarginFor("INSTAGRAM")).isEqualTo(Duration.ofMinutes(45));
        assertThat(service.minMarginFor("TIKTOK")).isEqualTo(Duration.ofMinutes(600));
    }

    @Test
    void theLimitsAreFiveMinutesAndOneDayForBoth() {
        service.update(5, 5);
        assertThat(service.view().minMarginMinutes()).isEqualTo(5);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(5);
        service.update(1440, 1440);
        assertThat(service.view().minMarginMinutes()).isEqualTo(1440);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(1440);
        assertThatThrownBy(() -> service.update(4, 480)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(30, 4)).hasMessageContaining("TikTok");
        assertThatThrownBy(() -> service.update(1441, 480)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(30, 1441)).isInstanceOf(IllegalArgumentException.class);
        // Nada se guardó con los rechazos
        assertThat(service.view().minMarginMinutes()).isEqualTo(1440);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(1440);
    }

    @Test
    void anInvalidTikTokValueSavedWithTheGeneralOneChangesNothing() {
        service.update(60, 600);
        assertThatThrownBy(() -> service.update(90, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.view().minMarginMinutes()).isEqualTo(60);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(600);
    }

    @Test
    void withoutTikTokInTheUpdateItStaysAsItWas() {
        service.update(60, 600);
        service.update(90);
        assertThat(service.view().minMarginMinutes()).isEqualTo(90);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(600);
    }

    @Test
    void aBrokenSavedValueFallsBackToItsDefault() {
        data.put(SettingStore.PUBLISH_MARGIN_MINUTES, "x");
        data.put(SettingStore.TIKTOK_MARGIN_MINUTES, "3");
        assertThat(service.view().minMarginMinutes()).isEqualTo(30);
        assertThat(service.view().tiktokMinMarginMinutes()).isEqualTo(480);
    }
}
