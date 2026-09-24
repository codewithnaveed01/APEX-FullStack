package com.apex.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Central JSON utility (Google Gson wrapper).
 * Every request/response body in the APEX backend flows through here.
 */
public final class Json {

    /** Nulls are omitted so the frontend sees the same shape it stored itself. */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() { }

    public static JsonElement parse(String s) {
        return JsonParser.parseString(s);
    }

    public static JsonObject parseObject(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static String stringify(Object o) {
        return GSON.toJson(o);
    }

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonArray arr() {
        return new JsonArray();
    }

    /* ---------- tolerant field readers ---------- */

    public static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    public static String str(JsonObject o, String key, String dflt) {
        String v = str(o, key);
        return v == null ? dflt : v;
    }

    public static int intVal(JsonObject o, String key, int dflt) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? dflt : e.getAsInt();
    }

    public static long longVal(JsonObject o, String key, long dflt) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? dflt : e.getAsLong();
    }

    public static boolean boolVal(JsonObject o, String key, boolean dflt) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? dflt : e.getAsBoolean();
    }

    /** Primary key of an entity object, as string (ids may be numeric or textual). */
    public static String idOf(JsonObject o) {
        JsonElement e = o.get("id");
        return (e == null || e.isJsonNull()) ? null : String.valueOf(e.getAsString());
    }
}
