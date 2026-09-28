package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class CustomerSearchRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    private final CustomerSearchRateLimiter rateLimiter = new CustomerSearchRateLimiter(clock);

    @Test
    void allowsTheBudgetPerCallerAndThenBlocks() {
        fillWindow("10.0.0.1");

        assertFalse(rateLimiter.tryAcquire("10.0.0.1"));
        assertTrue(rateLimiter.tryAcquire("10.0.0.2"));
    }

    @Test
    void startsAFreshWindowOnceTheOldOneHasPassed() {
        fillWindow("10.0.0.1");
        assertFalse(rateLimiter.tryAcquire("10.0.0.1"));

        clock.advanceBy(CustomerSearchRateLimiter.WINDOW);

        assertTrue(rateLimiter.tryAcquire("10.0.0.1"));
    }

    private void fillWindow(String caller) {
        for (var i = 0; i < CustomerSearchRateLimiter.MAX_SEARCHES_PER_WINDOW; i++) {
            assertTrue(rateLimiter.tryAcquire(caller));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceBy(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
