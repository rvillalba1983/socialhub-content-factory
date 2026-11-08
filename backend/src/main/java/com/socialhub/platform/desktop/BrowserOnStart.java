package com.socialhub.platform.desktop;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Con SocialHub instalado, al terminar de arrancar se abre en el navegador. */
@Component
class BrowserOnStart {

    @EventListener(ApplicationReadyEvent.class)
    void open() {
        if (Desktop.enabled()) Desktop.openBrowser();
    }
}
