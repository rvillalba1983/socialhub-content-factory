package com.socialhub.settings.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Dónde se guardan los ajustes generales (no los de una marca). */
@SecondaryPort
public interface SettingStore {

    String PUBLISH_MARGIN_MINUTES = "publish.min_margin_minutes";
    String TIKTOK_MARGIN_MINUTES = "publish.tiktok_min_margin_minutes";

    /** Carpeta local con los MP3 de Litoques (reels con sonido «tema de Litoques en el vídeo»). */
    String LITOQUES_AUDIO_FOLDER = "audio.litoques_folder";

    Optional<String> get(String key);

    void put(String key, String value);
}
