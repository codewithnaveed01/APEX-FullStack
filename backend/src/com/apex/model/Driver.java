package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** A driver. `data` is the frontend shape. */
public final class Driver {
    public final long id;
    public final String name, phone, city, license, status;
    public final int experience;
    public final boolean active;
    public final JsonObject data;

    private Driver(String id, String name, String phone, String city, Integer experience,
                   String license, Boolean active, String status, JsonObject data) {
        this.id = Long.parseLong(id);
        this.name = name; this.phone = phone; this.city = city;
        this.experience = experience == null ? 0 : experience;
        this.license = license; this.status = status;
        this.active = active == null || active;
        this.data = data;
    }

    public JsonObject toJson() { return data; }

    public static Driver fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        return new Driver(
                Json.clean(d.get("id") == null ? "" : d.get("id").getAsString()),
                Json.getStr(d, "name", ""),
                Json.getStr(d, "phone", ""),
                Json.getStr(d, "city", ""),
                d.has("experience") ? Integer.valueOf(Json.getInt(d, "experience", 0)) : null,
                Json.getStr(d, "license", ""),
                d.has("active") ? Boolean.valueOf(Json.getBool(d, "active", true)) : null,
                Json.getStr(d, "status", ""),
                d);
    }

    public static Driver fromRow(com.apex.db.QueryResult qr, int row) {
        String data = qr.col("data", row);
        JsonObject d = data == null ? new JsonObject() : safeParse(data);
        d.addProperty("id", Long.parseLong(qr.rows.get(row)[0]));
        return fromJson(d);
    }

    private static JsonObject safeParse(String s) {
        try {
            return com.google.gson.JsonParser.parseString(s).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }
}
