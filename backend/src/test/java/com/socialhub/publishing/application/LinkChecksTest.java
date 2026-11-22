package com.socialhub.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.publishing.domain.LinkProbe;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LinkChecksTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-16T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<LinkProbe.State> answer = new AtomicReference<>(LinkProbe.State.OK);
    private final LinkChecks checks = new LinkChecks(url -> { calls.incrementAndGet(); return answer.get(); }, clock);

    @Test
    void unknownUntilCheckedThenCachedWithoutGoingBackToTheNetwork() {
        assertThat(checks.known("https://a.es")).isEmpty();
        assertThat(checks.checkNow("https://a.es")).isEqualTo(LinkProbe.State.OK);
        assertThat(checks.known("https://a.es")).hasValue(LinkProbe.State.OK);
        assertThat(checks.known("https://a.es")).hasValue(LinkProbe.State.OK);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void failuresExpireSoonerThanSuccesses() {
        answer.set(LinkProbe.State.NOT_RESPONDING);
        checks.checkNow("https://roto.es");
        checks.checkNow("https://ok.es");
        answer.set(LinkProbe.State.OK);
        clock.now = clock.now.plus(LinkChecks.FAILED_VALID).plusSeconds(1);
        assertThat(checks.known("https://roto.es")).isEmpty();
        checks.checkNow("https://roto.es");
        assertThat(checks.known("https://roto.es")).hasValue(LinkProbe.State.OK);
        clock.now = clock.now.plus(LinkChecks.OK_VALID).plus(Duration.ofSeconds(1));
        assertThat(checks.known("https://ok.es")).isEmpty();
    }

    @Test
    void backgroundRequestChecksOnceAndNotifiesWhenDone() throws Exception {
        CountDownLatch done = new CountDownLatch(2);
        checks.request("https://a.es", done::countDown);
        checks.request("https://a.es", done::countDown); // ya en marcha o ya hecho: no duplica la llamada si coincide
        done.await(2, TimeUnit.SECONDS);
        assertThat(checks.known("https://a.es")).hasValue(LinkProbe.State.OK);
        assertThat(calls.get()).isBetween(1, 2);
    }
}
