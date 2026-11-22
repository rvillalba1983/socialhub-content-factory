package com.socialhub.launcher.adapter.in.startup;

import com.socialhub.launcher.Launcher;
import com.socialhub.platform.desktop.Desktop;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Con SocialHub instalado, al terminar de arrancar se aplica la preferencia de abrirse con el sistema (por defecto, activada). */
@Component
@RequiredArgsConstructor
class AutostartOnStart {

    private final Launcher launcher;

    @EventListener(ApplicationReadyEvent.class)
    void sync() {
        if (Desktop.enabled()) launcher.syncAutostart();
    }
}
