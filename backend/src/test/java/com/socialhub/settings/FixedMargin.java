package com.socialhub.settings;

import java.time.Duration;

/** Margen mínimo de publicación fijo, para las pruebas que no pasan por la base de datos. */
public final class FixedMargin implements PublishSettings {

    /** El margen que había antes del ajuste: muchas pruebas de horarios se escribieron con él. */
    public static final Duration SIX_HOURS = Duration.ofHours(6);

    private final Duration margin;

    private FixedMargin(Duration margin) { this.margin = margin; }

    public static FixedMargin of(Duration margin) { return new FixedMargin(margin); }

    @Override public Duration minMargin() { return margin; }

    @Override public View view() { return new View((int) margin.toMinutes(), DEFAULT_MINUTES, MIN_MINUTES, MAX_MINUTES); }

    @Override public View update(int minutes) { throw new UnsupportedOperationException(); }
}
