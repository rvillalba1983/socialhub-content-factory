package com.socialhub.settings.adapter.in.web;

import com.socialhub.api.SettingsApi;
import com.socialhub.api.model.PublishSettingsDto;
import com.socialhub.api.model.PublishSettingsInputDto;
import com.socialhub.api.model.AudioSettingsDto;
import com.socialhub.api.model.AudioSettingsInputDto;
import com.socialhub.settings.AudioSettings;
import com.socialhub.settings.PublishSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class SettingsController implements SettingsApi {

    private final PublishSettings settings;
    private final AudioSettings audio;

    @Override
    public ResponseEntity<PublishSettingsDto> getPublishSettings() {
        return ResponseEntity.ok(toDto(settings.view()));
    }

    @Override
    public ResponseEntity<PublishSettingsDto> updatePublishSettings(String requestGuard, PublishSettingsInputDto input) {
        return ResponseEntity.ok(toDto(settings.update(input.getMinMarginMinutes(), input.getTiktokMinMarginMinutes())));
    }

    @Override
    public ResponseEntity<AudioSettingsDto> getAudioSettings() {
        return ResponseEntity.ok(toDto(audio.view()));
    }

    @Override
    public ResponseEntity<AudioSettingsDto> updateAudioSettings(String requestGuard, AudioSettingsInputDto input) {
        return ResponseEntity.ok(toDto(audio.update(input.getLitoquesFolder())));
    }

    private static AudioSettingsDto toDto(AudioSettings.View view) {
        AudioSettingsDto dto = new AudioSettingsDto(view.available());
        dto.setLitoquesFolder(view.folder());
        return dto;
    }

    private static PublishSettingsDto toDto(PublishSettings.View view) {
        return new PublishSettingsDto(view.minMarginMinutes(), view.tiktokMinMarginMinutes(), view.defaultMinutes(),
                view.tiktokDefaultMinutes(), view.minMinutes(), view.maxMinutes());
    }
}
