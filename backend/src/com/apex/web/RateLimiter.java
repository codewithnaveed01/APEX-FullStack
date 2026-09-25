package com.apex.web;

import com.sun.net.httpserver.HttpExchange;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory rate limiter, keyed by IP (+ path-class).
 * - auth endpoints:       20 requests / 15 min
 * - uploads:              60 requests / hour
 * - live feed:         2,400 requests / 10 min (up to 20 active pollers per IP)
 * - fleet availability: 1,200 requests / 10 min (up to 30 active pollers per IP)
 * - everything else:     300 requests / 10 min
 * Polling is isolated so customers sharing an IP cannot exhaust the normal API budget.
 */
public final class RateLimiter {

    private static final class Bucket {
        final AtomicInteger count = new AtomicInteger();
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean blocked(HttpExchange ex, String path, String method) {
        long now = System.currentTimeMillis();
        String ip = clientIp(ex);
        String cls = classify(path, method);
        int limit;
        long windowMs;
        switch (cls) {
            case "auth":         limit = 20;   windowMs = 15L * 60 * 1000; break;
            case "upload":       limit = 60;   windowMs = 60L * 60 * 1000; break;
            case "live":         limit = 2400; windowMs = 10L * 60 * 1000; break;
            case "availability": limit = 1200; windowMs = 10L * 60 * 1000; break;
            default:             limit = 300;  windowMs = 10L * 60 * 1000; break;
        }
        String key = ip + "|" + cls;
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        long start = b.windowStart.get();
        if (now - start > windowMs) {
            if (b.windowStart.compareAndSet(start, now)) b.count.set(0);
        }
        maybePrune(now);
        return b.count.incrementAndGet() > limit;
    }

    private static String classify(String path, String method) {
        if (path.startsWith("/api/auth/") && "POST".equals(method)) return "auth";
        if (path.startsWith("/api/uploads")) return "upload";
        if ("GET".equals(method) && "/api/live".equals(path)) return "live";
        if ("GET".equals(method) && "/api/availability/fleet".equals(path)) return "availability";
        return "general";
    }

    private long lastPrune = 0;
    private synchronized void maybePrune(long now) {
        if (now - lastPrune > 60_000) {
            lastPrune = now;
            for (Map.Entry<String, Bucket> e : new HashMap<>(buckets).entrySet()) {
                if (now - e.getValue().windowStart.get() > 2 * 60 * 60 * 1000L) {
                    buckets.remove(e.getKey());
                }
            }
        }
    }

    private static String clientIp(HttpExchange ex) {
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        String real = ex.getRequestHeaders().getFirst("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return ex.getRemoteAddress() == null ? "local" : ex.getRemoteAddress().getHostString();
    }
}
