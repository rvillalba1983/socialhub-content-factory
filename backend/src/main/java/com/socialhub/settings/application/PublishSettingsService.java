package com.socialhub.settings.application;

import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.settings.PublishSettings;
import com.socialhub.settings.domain.SettingStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PublishSettingsService implements PublishSettings {

    private final SettingStore store;
    private final ApplicationEventPublisher events;

    @Override
    public Duration minMargin() { return Duration.ofMinutes(minutes()); }

    @Override
    public View view() { return new View(minutes(), DEFAULT_MINUTES, MIN_MINUTES, MAX_MINUTES); }

    @Override
    public View update(int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("El margen mínimo de publicación debe estar entre " + MIN_MINUTES
                    + " y " + MAX_MINUTES + " minutos");
        }
        store.put(SettingStore.PUBLISH_MARGIN_MINUTES, String.valueOf(minutes));
        events.publishEvent(new FactoryChanged("settings", null));
        return view();
    }

    /** Lo guardado, o el valor por defecto si no hay nada o no es válido (nunca baja del mínimo). */
    private int minutes() {
        return store.get(SettingStore.PUBLISH_MARGIN_MINUTES).map(PublishSettingsService::parse).orElse(DEFAULT_MINUTES);
    }

    private static int parse(String value) {
        try {
            int minutes = Integer.parseInt(value.strip());
            return minutes < MIN_MINUTES || minutes > MAX_MINUTES ? DEFAULT_MINUTES : minutes;
        } catch (NumberFormatException e) {
            return DEFAULT_MINUTES;
        }
    }
}
