package com.snakesafe.config;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.snakesafe.web.ApiException;

/** Small in-memory sliding-window limiter. Fine for one server; use a shared store if you scale out. */
@Component
public class RateLimiter {

    private static final int PURGE_THRESHOLD = 20_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int max, Duration window) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        if (hits.size() > PURGE_THRESHOLD) {
            hits.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || q.peekLast() < cutoff;
                }
            });
        }
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
                queue.pollFirst();
            }
            if (queue.size() >= max) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    public void check(String key, int max, Duration window) {
        if (!tryAcquire(key, max, window)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Please wait a little and try again.");
        }
    }
}
