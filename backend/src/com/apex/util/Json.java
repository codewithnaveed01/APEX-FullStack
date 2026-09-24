package com.apex.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.apex.web.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** JSON helpers built on gson. */
public final class Json {

    public static final Gson GSON = new Gson();
    private static final int MAX_BODY = 12 * 1024 * 1024; // 12 MB (uploads are 2.5 MB)

    private Json() { }

    public static JsonObject readObject(InputStream in) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[64 * 1024];
            StringBuilder sb = new StringBuilder();
            int n;
            long total = 0;
            while ((n = r.read(buf)) > 0) {
                total += n;
                if (total > MAX_BODY) throw ApiException.validation("Request body too large");
                sb.append(buf, 0, n);
            }
            String s = sb.toString().trim();
            if (s.isEmpty()) return new JsonObject();
            try {
                JsonElement el = JsonParser.parseString(s);
                if (el == null || !el.isJsonObject()) {
                    throw ApiException.validation("Request body must be a JSON object");
                }
                return el.getAsJsonObject();
            } catch (JsonSyntaxException e) {
                throw ApiException.bad("Invalid JSON body");
            }
        } catch (IOException e) {
            throw ApiException.bad("Cannot read request body: " + e.getMessage());
        }
    }

    public static JsonObject readFile(Path p) {
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            JsonElement el = JsonParser.parseString(s.isEmpty() ? "{}" : s);
            if (!el.isJsonObject()) throw ApiException.validation("Expected a JSON object in " + p.getFileName());
            return el.getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON file " + p + ": " + e.getMessage(), e);
        }
    }

    /* -------- field accessors (tolerant, NPE-free) -------- */

    public static String getStr(JsonObject o, String k, String dflt) {
        if (o == null) return dflt;
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return dflt;
        String s = e.getAsString();
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    public static long getLong(JsonObject o, String k, long dflt) {
        if (o == null) return dflt;
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return dflt;
        try {
            if (e.isJsonPrimitive()) {
                var p = e.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsLong();
                if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
                String s = p.getAsString().trim();
                if (s.isEmpty()) return dflt;
                return Long.parseLong(s.replaceAll("[^0-9-]", ""));
            }
        } catch (RuntimeException ignored) { }
        return dflt;
    }

    public static int getInt(JsonObject o, String k, int dflt) {
        return (int) getLong(o, k, dflt);
    }

    public static boolean getBool(JsonObject o, String k, boolean dflt) {
        if (o == null) return dflt;
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return dflt;
        if (e.isJsonPrimitive()) {
            var p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            String s = p.getAsString();
            if (s != null && !s.isEmpty()) return s.equalsIgnoreCase("true");
        }
        return dflt;
    }

    public static JsonObject obj(JsonObject o, String k) {
        if (o == null) return null;
        JsonElement e = o.get(k);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }

    public static JsonObject orEmpty(JsonObject o) {
        return o == null ? new JsonObject() : o;
    }

    public static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    @SuppressWarnings("unchecked")
    public static String cleanMapKey(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString().trim();
    }
}
