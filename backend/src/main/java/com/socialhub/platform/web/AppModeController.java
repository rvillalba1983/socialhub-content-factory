package com.socialhub.platform.web;

import com.socialhub.api.AppModeApi;
import com.socialhub.api.model.AppModeChangeDto;
import com.socialhub.api.model.AppModeDto;
import com.socialhub.api.model.AppModeStateDto;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class AppModeController implements AppModeApi {

    private final AppModeHolder mode;

    @Override
    public ResponseEntity<AppModeStateDto> getAppMode() {
        return ResponseEntity.ok(state());
    }

    @Override
    public ResponseEntity<AppModeStateDto> changeAppMode(String requestGuard, AppModeChangeDto change) {
        mode.switchTo(AppMode.valueOf(change.getMode().name()));
        return ResponseEntity.ok(state());
    }

    private AppModeStateDto state() {
        return new AppModeStateDto(AppModeDto.valueOf(mode.current().name()), mode.demoEnabled());
    }
}
