package com.socialhub.demo.domain;

import java.time.Instant;
import java.time.LocalDate;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** La base de datos del modo demo (nunca la del modo real). */
@SecondaryPort
public interface DemoDatabase {

    /** No tiene ni una marca todavía. */
    boolean isEmpty();

    /** Carga los datos de ejemplo, marcados como demostración y colocados alrededor de {@code today}. */
    void load(DemoData data, LocalDate today, Instant now);
}
