package com.socialhub.demo.adapter.in.startup;

import com.socialhub.demo.DemoSeeding;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Al arrancar, antes que nada: los datos de ejemplo del modo demo. */
@Component
@Order(0)
@RequiredArgsConstructor
class DemoDataRunner implements ApplicationRunner {

    private final DemoSeeding seeding;

    @Override
    public void run(ApplicationArguments args) {
        seeding.seedIfEmpty();
    }
}
