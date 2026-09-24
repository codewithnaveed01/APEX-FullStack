package com.apex.web;

import com.apex.util.Json;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Small helpers shared by every HTTP handler. */
public final class HttpUtil {

    private HttpUtil() { }

    /** Hard cap on request bodies (5 MB) so nobody can flood memory. */
    public static final int MAX_BODY = 5 * 1024 * 1024;

    public static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) throw new IllegalArgumentException("Request body too large");
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /** Security headers on every response. */
    private static void secure(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }

    public static void sendJson(HttpExchange ex, int code, Object payload) throws IOException {
        String text = payload instanceof String ? (String) payload : Json.stringify(payload);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        secure(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    public static void sendError(HttpExchange ex, int code, String message) throws IOException {
        com.google.gson.JsonObject o = Json.obj();
        o.addProperty("error", message);
        sendJson(ex, code, o);
    }

    public static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            if (part.substring(0, eq).equals(key)) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    public static String clientIp(HttpExchange ex) {
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return ex.getRemoteAddress() == null ? "unknown" : ex.getRemoteAddress().getAddress().getHostAddress();
    }

    public static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7).trim();
        return null;
    }
}
