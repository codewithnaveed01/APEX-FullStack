package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** A car in the fleet. `data` is the exact frontend shape (round-trips). */
public final class Car {
    public final long id;
    public final String name, brand, category, origin, image, carCondition, status, marketNote, ownerId;
    public final int year, seats;
    public final long rate, hourlyRate, deposit;
    public final String engine, power, fuel, km, color, plate;
    public final JsonObject data;

    private Car(String id, String name, String brand, String category, String origin, String image,
                Integer year, Long rate, Long hourlyRate, Long deposit, Integer seats,
                String engine, String power, String fuel, String km, String color, String plate,
                String condition, String status, String marketNote, String ownerId, JsonObject data) {
        this.id = Long.parseLong(id);
        this.name = name; this.brand = brand; this.category = category; this.origin = origin;
        this.image = image; this.carCondition = condition; this.status = status;
        this.marketNote = marketNote; this.ownerId = ownerId;
        this.year = year == null ? 0 : year;
        this.seats = seats == null ? 0 : seats;
        this.rate = rate == null ? 0 : rate;
        this.hourlyRate = hourlyRate == null ? 0 : hourlyRate;
        this.deposit = deposit == null ? 0 : deposit;
        this.engine = engine; this.power = power; this.fuel = fuel; this.km = km;
        this.color = color; this.plate = plate;
        this.data = data;
    }

    public JsonObject toJson() { return data; }

    public static Car fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        return new Car(
                Json.clean(d.get("id") == null ? "" : d.get("id").getAsString()),
                Json.getStr(d, "name", ""),
                Json.getStr(d, "brand", ""),
                Json.getStr(d, "category", ""),
                Json.getStr(d, "origin", ""),
                Json.getStr(d, "image", ""),
                Json.obj(d, "year") != null || d.has("year") ? Integer.valueOf(Json.getInt(d, "year", 0)) : null,
                d.has("rate") ? Long.valueOf(Json.getLong(d, "rate", 0)) : null,
                d.has("hourlyRate") ? Long.valueOf(Json.getLong(d, "hourlyRate", 0)) : null,
                d.has("deposit") ? Long.valueOf(Json.getLong(d, "deposit", 0)) : null,
                d.has("seats") ? Integer.valueOf(Json.getInt(d, "seats", 0)) : null,
                Json.getStr(d, "engine", ""),
                Json.getStr(d, "power", ""),
                Json.getStr(d, "fuel", ""),
                Json.getStr(d, "km", ""),
                Json.getStr(d, "color", ""),
                Json.getStr(d, "plate", ""),
                Json.getStr(d, "condition", ""),
                Json.getStr(d, "status", "Active"),
                Json.getStr(d, "marketNote", ""),
                Json.getStr(d, "ownerId", ""),
                d);
    }

    public static Car fromRow(com.apex.db.QueryResult qr, int row) {
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
