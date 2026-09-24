package com.apex.model;

import com.apex.util.Json;
import com.google.gson.JsonObject;

/** Owner vehicle application. `data` is the frontend shape. */
public final class Application {
    public final String id, userId, owner, phone, email, brand, model, registration, cnic, city, preference, availableFrom, status, verification;
    public final int year, mileage, photoCount;
    public final long rate;
    public final JsonObject data;

    private Application(String id, String userId, String owner, String phone, String email,
                        String brand, String model, Integer year, String registration, String cnic,
                        String license, String city, Integer mileage, String condition, Long rate,
                        String preference, String availableFrom, Integer photoCount, String notes,
                        String status, String verification, JsonObject data) {
        this.id = id; this.userId = userId; this.owner = owner; this.phone = phone;
        this.email = email; this.brand = brand; this.model = model;
        this.year = year == null ? 0 : year;
        this.registration = registration; this.cnic = cnic; this.city = city;
        this.mileage = mileage == null ? 0 : mileage;
        this.rate = rate == null ? 0 : rate;
        this.preference = preference; this.availableFrom = availableFrom;
        this.photoCount = photoCount == null ? 0 : photoCount;
        this.status = status; this.verification = verification;
        this.data = data;
    }

    public JsonObject toJson() { return data; }

    public static Application fromJson(JsonObject j) {
        if (j == null) return null;
        JsonObject d = j.deepCopy();
        return new Application(
                Json.getStr(d, "id", ""),
                Json.getStr(d, "userId", ""),
                Json.getStr(d, "owner", ""),
                Json.getStr(d, "phone", ""),
                Json.getStr(d, "email", ""),
                Json.getStr(d, "brand", ""),
                Json.getStr(d, "model", ""),
                d.has("year") ? Integer.valueOf(Json.getInt(d, "year", 0)) : null,
                Json.getStr(d, "registration", ""),
                Json.getStr(d, "cnic", ""),
                Json.getStr(d, "license", ""),
                Json.getStr(d, "city", ""),
                d.has("mileage") ? Integer.valueOf(Json.getInt(d, "mileage", 0)) : null,
                Json.getStr(d, "condition", ""),
                d.has("rate") ? Long.valueOf(Json.getLong(d, "rate", 0)) : null,
                Json.getStr(d, "preference", ""),
                Json.getStr(d, "availableFrom", ""),
                d.has("photoCount") ? Integer.valueOf(Json.getInt(d, "photoCount", 0)) : null,
                Json.getStr(d, "notes", ""),
                Json.getStr(d, "status", "Submitted"),
                Json.getStr(d, "verification", ""),
                d);
    }

    public static Application fromRow(com.apex.db.QueryResult qr, int row) {
        String data = qr.col("data", row);
        JsonObject d = data == null ? new JsonObject() : safeParse(data);
        d.addProperty("id", qr.rows.get(row)[0]);
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
