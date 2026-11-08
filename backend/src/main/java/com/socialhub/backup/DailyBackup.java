package com.socialhub.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La copia diaria: poco después de abrir SocialHub y, si sigue abierto de un día para otro, al cambiar de día
 * (se comprueba cada 10 minutos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DailyBackup {

    private final BackupService backups;

    @Scheduled(initialDelayString = "${socialhub.backup.initial-delay-millis:20000}",
               fixedDelayString = "${socialhub.backup.check-millis:600000}")
    void run() {
        try {
            backups.dailyIfNeeded();
        } catch (RuntimeException e) {
            log.warn("No se pudo hacer la copia diaria: {}", e.toString());
        }
    }
}
