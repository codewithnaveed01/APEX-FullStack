package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** A car in the fleet. `data` is the exact frontend shape (round-trips). */
public final class Car {
    public final long id;
    public final String name, brand, category, origin, image, carCondition, status, marketNote, ownerId, applicationId;
    public final int year, seats;
    public final long rate, hourlyRate, deposit, mainImageId, interiorImageId, detailImageId;
    public final String engine, power, fuel, km, color, plate;
    public final JsonObject data;

    private Car(String id, String name, String brand, String category, String origin, String image,
                Integer year, Long rate, Long hourlyRate, Long deposit, Integer seats,
                String engine, String power, String fuel, String km, String color, String plate,
                String condition, String status, String marketNote, String ownerId, String applicationId,
                Long mainImageId, Long interiorImageId, Long detailImageId, JsonObject data) {
        this.id = Long.parseLong(id);
        this.name = name; this.brand = brand; this.category = category; this.origin = origin;
        this.image = image; this.carCondition = condition; this.status = status;
        this.marketNote = marketNote; this.ownerId = ownerId; this.applicationId = applicationId;
        this.year = year == null ? 0 : year; this.seats = seats == null ? 0 : seats;
        this.rate = rate == null ? 0 : rate; this.hourlyRate = hourlyRate == null ? 0 : hourlyRate;
        this.deposit = deposit == null ? 0 : deposit;
        this.mainImageId = mainImageId == null ? 0 : mainImageId;
        this.interiorImageId = interiorImageId == null ? 0 : interiorImageId;
        this.detailImageId = detailImageId == null ? 0 : detailImageId;
        this.engine = engine; this.power = power; this.fuel = fuel; this.km = km;
        this.color = color; this.plate = plate; this.data = data;
    }

    public JsonObject toJson() { return data.deepCopy(); }

    public static Car fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        String id = d.has("id") ? Json.clean(d.get("id").getAsString()) : "";
        return new Car(id,
                Json.getStr(d, "name", ""), Json.getStr(d, "brand", ""),
                Json.getStr(d, "category", ""), Json.getStr(d, "origin", ""),
                Json.getStr(d, "image", ""), d.has("year") ? Json.getInt(d, "year", 0) : null,
                d.has("rate") ? Json.getLong(d, "rate", 0) : null,
                d.has("hourlyRate") ? Json.getLong(d, "hourlyRate", 0) : null,
                d.has("deposit") ? Json.getLong(d, "deposit", 0) : null,
                d.has("seats") ? Json.getInt(d, "seats", 0) : null,
                Json.getStr(d, "engine", ""), Json.getStr(d, "power", ""),
                Json.getStr(d, "fuel", ""), Json.getStr(d, "km", ""),
                Json.getStr(d, "color", ""), Json.getStr(d, "plate", ""),
                Json.getStr(d, "condition", ""), Json.getStr(d, "status", "Active"),
                Json.getStr(d, "marketNote", ""), Json.getStr(d, "ownerId", ""),
                Json.getStr(d, "applicationId", ""),
                d.has("mainImageId") ? Json.getLong(d, "mainImageId", 0) : null,
                d.has("interiorImageId") ? Json.getLong(d, "interiorImageId", 0) : null,
                d.has("detailImageId") ? Json.getLong(d, "detailImageId", 0) : null, d);
    }

    public static Car fromRow(com.apex.db.QueryResult qr, int row) {
        String raw = qr.col("data", row);
        JsonObject d = raw == null ? new JsonObject() : safeParse(raw);
        d.addProperty("id", Long.parseLong(qr.col("id", row)));
        put(d, "ownerId", qr.col("owner_id", row));
        put(d, "applicationId", qr.col("application_id", row));
        putNumber(d, "mainImageId", qr.col("main_image_id", row));
        putNumber(d, "interiorImageId", qr.col("interior_image_id", row));
        putNumber(d, "detailImageId", qr.col("detail_image_id", row));
        return fromJson(d);
    }

    private static void put(JsonObject d, String key, String value) {
        if (value != null && !value.isBlank()) d.addProperty(key, value);
    }
    private static void putNumber(JsonObject d, String key, String value) {
        if (value != null && !value.isBlank()) {
            try { d.addProperty(key, Long.parseLong(value)); } catch (NumberFormatException ignored) { }
        }
    }
    private static JsonObject safeParse(String s) {
        try { return com.google.gson.JsonParser.parseString(s).getAsJsonObject(); }
        catch (RuntimeException e) { return new JsonObject(); }
    }
}
