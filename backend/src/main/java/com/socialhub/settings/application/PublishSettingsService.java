package com.socialhub.settings.application;

import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.settings.PublishSettings;
import com.socialhub.settings.domain.SettingStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PublishSettingsService implements PublishSettings {

    private final SettingStore store;
    private final ApplicationEventPublisher events;

    @Override
    public Duration minMarginFor(String platform) {
        return TIKTOK.equals(platform)
                ? Duration.ofMinutes(minutes(SettingStore.TIKTOK_MARGIN_MINUTES, TIKTOK_DEFAULT_MINUTES))
                : Duration.ofMinutes(minutes(SettingStore.PUBLISH_MARGIN_MINUTES, DEFAULT_MINUTES));
    }

    @Override
    public View view() {
        return new View(minutes(SettingStore.PUBLISH_MARGIN_MINUTES, DEFAULT_MINUTES),
                minutes(SettingStore.TIKTOK_MARGIN_MINUTES, TIKTOK_DEFAULT_MINUTES),
                DEFAULT_MINUTES, TIKTOK_DEFAULT_MINUTES, MIN_MINUTES, MAX_MINUTES);
    }

    @Override
    public View update(int minutes, @Nullable Integer tiktokMinutes) {
        check("El margen mínimo de publicación", minutes);
        if (tiktokMinutes != null) check("El margen mínimo de publicación en TikTok", tiktokMinutes);
        store.put(SettingStore.PUBLISH_MARGIN_MINUTES, String.valueOf(minutes));
        if (tiktokMinutes != null) store.put(SettingStore.TIKTOK_MARGIN_MINUTES, String.valueOf(tiktokMinutes));
        events.publishEvent(new FactoryChanged("settings", null));
        return view();
    }

    private static void check(String what, int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(what + " debe estar entre " + MIN_MINUTES + " y " + MAX_MINUTES + " minutos");
        }
    }

    /** Lo guardado, o el valor por defecto si no hay nada o no es válido (nunca baja del mínimo). */
    private int minutes(String key, int fallback) {
        return store.get(key).map(v -> parse(v, fallback)).orElse(fallback);
    }

    private static int parse(String value, int fallback) {
        try {
            int minutes = Integer.parseInt(value.strip());
            return minutes < MIN_MINUTES || minutes > MAX_MINUTES ? fallback : minutes;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
