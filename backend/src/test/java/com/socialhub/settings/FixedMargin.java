package com.socialhub.settings;

import java.time.Duration;

/** Márgenes mínimos de publicación fijos, para las pruebas que no pasan por la base de datos. */
public final class FixedMargin implements PublishSettings {

    /** El margen que había antes del ajuste: muchas pruebas de horarios se escribieron con él. */
    public static final Duration SIX_HOURS = Duration.ofHours(6);

    private final Duration margin;
    private final Duration tiktok;

    private FixedMargin(Duration margin, Duration tiktok) { this.margin = margin; this.tiktok = tiktok; }

    /** El mismo margen para todas las redes. */
    public static FixedMargin of(Duration margin) { return new FixedMargin(margin, margin); }

    /** Un margen general y otro para TikTok. */
    public static FixedMargin of(Duration margin, Duration tiktok) { return new FixedMargin(margin, tiktok); }

    @Override public Duration minMarginFor(String platform) { return TIKTOK.equals(platform) ? tiktok : margin; }

    @Override public View view() {
        return new View((int) margin.toMinutes(), (int) tiktok.toMinutes(), DEFAULT_MINUTES, TIKTOK_DEFAULT_MINUTES, MIN_MINUTES, MAX_MINUTES);
    }

    @Override public View update(int minutes, @org.jspecify.annotations.Nullable Integer tiktokMinutes) { throw new UnsupportedOperationException(); }
}
