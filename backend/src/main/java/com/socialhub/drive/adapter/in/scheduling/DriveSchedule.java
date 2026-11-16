package com.socialhub.drive.adapter.in.scheduling;

import com.socialhub.drive.DriveCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** El reloj de la comprobación de Drive: cada 30 s. */
@Component
@RequiredArgsConstructor
class DriveSchedule {

    private final DriveCheck drive;

    @Scheduled(initialDelayString = "${socialhub.drive.check-initial-delay-millis:5000}",
               fixedDelayString = "${socialhub.drive.check-millis:30000}")
    void check() { drive.check(); }
}
