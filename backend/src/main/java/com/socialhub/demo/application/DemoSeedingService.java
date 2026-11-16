package com.socialhub.demo.application;

import com.socialhub.demo.DemoSeeding;
import com.socialhub.demo.domain.DemoData;
import com.socialhub.demo.domain.DemoDataSource;
import com.socialhub.demo.domain.DemoDatabase;
import com.socialhub.platform.SocialHubProperties;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Carga los datos de ejemplo (marcados con is_demo) la primera vez que la base de datos del modo demo está vacía. */
@Slf4j
@Service
@RequiredArgsConstructor
class DemoSeedingService implements DemoSeeding {

    private final DemoDatabase database;
    private final DemoDataSource source;
    private final SocialHubProperties properties;
    private final Clock clock;

    @Override
    public void seedIfEmpty() {
        if (!properties.demo().enabled()) return;
        if (!database.isEmpty()) return;
        DemoData data = source.read();
        database.load(data, LocalDate.now(clock), clock.instant());
        log.info("Datos de ejemplo cargados (marcados como demostración)");
    }
}
