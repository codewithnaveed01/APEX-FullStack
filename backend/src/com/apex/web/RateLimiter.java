package com.apex.web;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny in-memory per-key (IP) rate limiter + brute-force lockout.
 * Good enough for a single-node deployment; swap for Redis-backed
 * limiting when scaling out.
 */
public final class RateLimiter {

    private static final Map<String, ArrayDeque<Long>> HITS = new HashMap<>();
    private static final Map<String, long[]> FAILS = new HashMap<>();   // {count, windowStart}
    private static final Map<String, Long> LOCKS = new HashMap<>();     // key -> locked-until

    private RateLimiter() { }

    /** Sliding window: at most max calls per windowMs for this key. */
    public static synchronized boolean allow(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = HITS.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
        if (q.size() >= max) return false;
        q.addLast(now);
        return true;
    }

    public static synchronized boolean locked(String key) {
        Long until = LOCKS.get(key);
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Registers a failed attempt; after maxFails inside windowMs the key is
     * locked for lockMs. Returns true when the lock has just been triggered.
     */
    public static synchronized boolean strike(String key, int maxFails, long windowMs, long lockMs) {
        long now = System.currentTimeMillis();
        long[] f = FAILS.get(key);
        if (f == null || now - f[1] > windowMs) {
            f = new long[]{0, now};
            FAILS.put(key, f);
        }
        f[0]++;
        if (f[0] >= maxFails) {
            LOCKS.put(key, now + lockMs);
            FAILS.remove(key);
            return true;
        }
        return false;
    }

    public static synchronized void clearStrikes(String key) {
        FAILS.remove(key);
        LOCKS.remove(key);
    }
}
