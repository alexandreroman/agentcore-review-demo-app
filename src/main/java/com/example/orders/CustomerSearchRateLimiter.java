package com.example.orders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter that caps how many customer searches a single caller may run. It makes
 * sweeping the customers table with many cheap requests impractical, but it is no replacement for
 * authentication: the service itself has none, so it must be deployed behind an authenticating
 * gateway.
 */
@Component
class CustomerSearchRateLimiter {

    static final int MAX_SEARCHES_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final Clock clock;
    private final Map<String, Window> windowsByCaller = new ConcurrentHashMap<>();

    CustomerSearchRateLimiter() {
        this(Clock.systemUTC());
    }

    CustomerSearchRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one search for the caller and reports whether it stays within the allowed budget.
     */
    boolean tryAcquire(String caller) {
        var now = clock.instant();
        if (windowsByCaller.size() > MAX_TRACKED_CALLERS) {
            windowsByCaller.values().removeIf(window -> window.hasEnded(now));
        }
        var window = windowsByCaller.compute(caller, (key, current) -> {
            if (current == null || current.hasEnded(now)) {
                return new Window(now, 1);
            }
            return current.count() > MAX_SEARCHES_PER_WINDOW ? current : current.increment();
        });
        return window.count() <= MAX_SEARCHES_PER_WINDOW;
    }

    private record Window(Instant startedAt, int count) {

        boolean hasEnded(Instant now) {
            return !now.isBefore(startedAt.plus(WINDOW));
        }

        Window increment() {
            return new Window(startedAt, count + 1);
        }
    }
}
