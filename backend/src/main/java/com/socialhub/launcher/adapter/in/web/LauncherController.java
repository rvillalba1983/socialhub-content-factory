package com.socialhub.launcher.adapter.in.web;

import com.socialhub.api.LauncherApi;
import com.socialhub.api.model.AutostartDto;
import com.socialhub.api.model.AutostartInputDto;
import com.socialhub.launcher.Launcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class LauncherController implements LauncherApi {

    private final Launcher launcher;

    @Override
    public ResponseEntity<AutostartDto> getAutostart() {
        return ResponseEntity.ok(toDto(launcher.autostart()));
    }

    @Override
    public ResponseEntity<AutostartDto> setAutostart(String requestGuard, AutostartInputDto input) {
        return ResponseEntity.ok(toDto(launcher.setAutostart(input.getEnabled())));
    }

    private static AutostartDto toDto(Launcher.Autostart state) {
        return new AutostartDto(state.available(), state.enabled());
    }
}
