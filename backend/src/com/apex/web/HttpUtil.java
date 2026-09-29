package com.apex.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Response writers and query parsing. */
public final class HttpUtil {

    private HttpUtil() { }

    public static void sendJson(HttpExchange ex, int status, JsonElement body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        boolean useGzip = bytes.length >= 1024 && acceptsGzip(ex);
        if (useGzip) bytes = gzip(bytes);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Vary", "Accept-Encoding");
        if (useGzip) ex.getResponseHeaders().set("Content-Encoding", "gzip");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static boolean acceptsGzip(HttpExchange ex) {
        String value = ex.getRequestHeaders().getFirst("Accept-Encoding");
        return value != null && value.toLowerCase().contains("gzip");
    }

    public static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(256, input.length / 3));
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) { gzip.write(input); }
        return out.toByteArray();
    }

    public static void sendError(HttpExchange ex, int status, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", message == null ? "Request failed" : message);
        sendJson(ex, status, o);
    }

    public static void sendBytes(HttpExchange ex, int status, String contentType, byte[] body,
                                 String cacheControl) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body;
        ex.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        ex.getResponseHeaders().set("Cache-Control", cacheControl == null ? "no-store" : cacheControl);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i < 0) continue;
            try {
                m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"),
                      URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
            } catch (Exception ignored) { }
        }
        return m;
    }

    public static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) return null;
        h = h.trim();
        if (h.startsWith("Bearer ")) return h.substring(7).trim();
        if (h.startsWith("bearer ")) return h.substring(7).trim();
        return h;
    }
}
